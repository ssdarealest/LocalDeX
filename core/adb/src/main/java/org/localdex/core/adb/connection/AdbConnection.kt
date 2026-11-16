package org.localdex.core.adb.connection

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.localdex.core.adb.crypto.AdbCrypto
import org.localdex.core.adb.protocol.AdbMessage
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Manages an active loopback ADB session over 127.0.0.1 with stream multiplexing.
 */
class AdbConnection(
    private val context: Context,
    val host: String = "127.0.0.1",
    val port: Int,
    private val useTls: Boolean = true,
    private val adbCrypto: AdbCrypto = AdbCrypto.getOrCreate(context)
) : Closeable {

    companion object {
        private const val TAG = "AdbConnection"
        private const val CONNECT_TIMEOUT_MS = 10000
    }

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val writeMutex = Mutex()
    private val streamIdGenerator = AtomicInteger(1)
    private val activeStreams = ConcurrentHashMap<Int, AdbStream>()
    private val streamOpenCompletions = ConcurrentHashMap<Int, kotlinx.coroutines.CompletableDeferred<AdbStream>>()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var readerJob: Job? = null
    private var isConnected = false

    /**
     * Connects and completes the ADB connection handshake.
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Connecting to ADB daemon at $host:$port (useTls: $useTls)...")

            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

            if (useTls) {
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setKeyEntry("client", adbCrypto.privateKey, null, arrayOf(adbCrypto.certificate))
                }
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                    init(keyStore, null)
                }
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                })
                val sslContext = SSLContext.getInstance("TLSv1.3").apply {
                    init(kmf.keyManagers, trustAllCerts, java.security.SecureRandom())
                }
                val sslSocket = sslContext.socketFactory.createSocket(
                    rawSocket,
                    host,
                    port,
                    true
                ) as SSLSocket
                sslSocket.useClientMode = true
                sslSocket.startHandshake()
                socket = sslSocket
            } else {
                socket = rawSocket
            }

            inputStream = socket!!.getInputStream()
            outputStream = socket!!.getOutputStream()

            // 1. Send initial CNXN packet
            val banner = "host::LocalDeX\u0000".toByteArray(Charsets.UTF_8)
            val cnxnMessage = AdbMessage(
                command = AdbMessage.A_CNXN,
                arg0 = AdbMessage.ADB_VERSION,
                arg1 = AdbMessage.MAX_PAYLOAD,
                data = banner
            )
            sendMessage(cnxnMessage)

            // 2. Read handshake response
            val response = AdbMessage.readFrom(inputStream!!)
            if (response.command == AdbMessage.A_AUTH) {
                handleAuthChallenge(response)
            } else if (response.command == AdbMessage.A_CNXN) {
                Log.i(TAG, "Connected directly without challenge: ${String(response.data)}")
            } else {
                throw IllegalStateException("Unexpected response to CNXN: ${response.command}")
            }

            isConnected = true
            startReaderLoop()
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ADB daemon", e)
            close()
            return@withContext false
        }
    }

    private suspend fun handleAuthChallenge(authMessage: AdbMessage) {
        if (authMessage.arg0 == AdbMessage.AUTH_TYPE_TOKEN) {
            Log.i(TAG, "Received AUTH token challenge; signing with local RSA key...")
            val signedToken = adbCrypto.signAdbToken(authMessage.data)
            val authResponse = AdbMessage(
                command = AdbMessage.A_AUTH,
                arg0 = AdbMessage.AUTH_TYPE_SIGNATURE,
                arg1 = 0,
                data = signedToken
            )
            sendMessage(authResponse)

            val reply = AdbMessage.readFrom(inputStream!!)
            if (reply.command == AdbMessage.A_CNXN) {
                Log.i(TAG, "Auth successful via signature: ${String(reply.data)}")
            } else if (reply.command == AdbMessage.A_AUTH) {
                // If signature failed, send public key
                Log.i(TAG, "Signature not recognized; sending public key string...")
                val pubKeyString = adbCrypto.getAdbPublicKeyString().toByteArray(Charsets.UTF_8)
                val pubKeyMsg = AdbMessage(
                    command = AdbMessage.A_AUTH,
                    arg0 = AdbMessage.AUTH_TYPE_RSA_PUBLIC,
                    arg1 = 0,
                    data = pubKeyString
                )
                sendMessage(pubKeyMsg)

                val finalReply = AdbMessage.readFrom(inputStream!!)
                if (finalReply.command != AdbMessage.A_CNXN) {
                    throw IllegalStateException("Failed to authenticate with public key: cmd ${finalReply.command}")
                }
                Log.i(TAG, "Auth successful via public key")
            }
        }
    }

    private fun startReaderLoop() {
        readerJob = scope.launch {
            try {
                while (isActive && isConnected) {
                    val message = AdbMessage.readFrom(inputStream!!)
                    when (message.command) {
                        AdbMessage.A_OKAY -> {
                            val localId = message.arg1
                            val remoteId = message.arg0
                            val stream = activeStreams[localId]
                            if (stream != null) {
                                stream.remoteId = remoteId
                                streamOpenCompletions.remove(localId)?.complete(stream)
                            }
                        }
                        AdbMessage.A_WRTE -> {
                            val localId = message.arg1
                            val remoteId = message.arg0
                            val stream = activeStreams[localId]
                            if (stream != null) {
                                stream.readChannel.trySend(message.data)
                                // Send OKAY acknowledgment back
                                val ack = AdbMessage(
                                    command = AdbMessage.A_OKAY,
                                    arg0 = localId,
                                    arg1 = remoteId
                                )
                                sendMessage(ack)
                            }
                        }
                        AdbMessage.A_CLSE -> {
                            val localId = message.arg1
                            activeStreams[localId]?.onClosedByRemote()
                        }
                    }
                }
            } catch (e: Exception) {
                if (isConnected) {
                    Log.w(TAG, "ADB reader loop encountered error: ${e.message}")
                }
            } finally {
                close()
            }
        }
    }

    /**
     * Opens a new multiplexed ADB stream (e.g. "shell:ls -la" or "sync:").
     */
    suspend fun openStream(destination: String): AdbStream = withContext(Dispatchers.IO) {
        val localId = streamIdGenerator.incrementAndGet()
        val stream = AdbStream(localId, this@AdbConnection, destination)
        val deferred = kotlinx.coroutines.CompletableDeferred<AdbStream>()

        activeStreams[localId] = stream
        streamOpenCompletions[localId] = deferred

        val openMessage = AdbMessage(
            command = AdbMessage.A_OPEN,
            arg0 = localId,
            arg1 = 0,
            data = "$destination\u0000".toByteArray(Charsets.UTF_8)
        )
        sendMessage(openMessage)

        return@withContext deferred.await()
    }

    /**
     * Executes a single shell command and returns the full output string.
     */
    suspend fun executeShell(command: String): String = withContext(Dispatchers.IO) {
        val stream = openStream("shell:$command")
        return@withContext stream.readFullyToString()
    }

    /**
     * Pushes a file directly onto the device filesystem (e.g. `/data/local/tmp/scrcpy-server.jar`).
     */
    suspend fun pushFile(data: ByteArray, remotePath: String, permissionsMode: Int = 493): Boolean = withContext(Dispatchers.IO) {
        val stream = openStream("sync:")
        try {
            // SEND command: "SEND" + path,mode (e.g., "/data/local/tmp/scrcpy-server.jar,493")
            val sendArg = "$remotePath,$permissionsMode".toByteArray(Charsets.UTF_8)
            val sendHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .put("SEND".toByteArray())
                .putInt(sendArg.size)
                .array()
            stream.write(sendHeader + sendArg)

            // DATA chunks (max 64KB per chunk)
            val chunkSize = 64 * 1024
            var offset = 0
            while (offset < data.size) {
                val len = (data.size - offset).coerceAtMost(chunkSize)
                val dataHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .put("DATA".toByteArray())
                    .putInt(len)
                    .array()
                val chunk = ByteArray(len)
                System.arraycopy(data, offset, chunk, 0, len)
                stream.write(dataHeader + chunk)
                offset += len
            }

            // DONE command
            val doneHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .put("DONE".toByteArray())
                .putInt(System.currentTimeMillis().toInt() / 1000)
                .array()
            stream.write(doneHeader)

            // Await OKAY response from adbd
            val responseChunk = stream.readChannel.receive()
            val status = String(responseChunk.take(4).toByteArray(), Charsets.US_ASCII)
            return@withContext status == "OKAY"
        } catch (e: Exception) {
            Log.e(TAG, "Push file failed for $remotePath", e)
            return@withContext false
        } finally {
            stream.close()
        }
    }

    internal suspend fun sendMessage(message: AdbMessage) {
        writeMutex.withLock {
            outputStream?.let { message.writeTo(it) }
        }
    }

    internal fun closeStream(stream: AdbStream) {
        val localId = stream.localId
        val remoteId = stream.remoteId
        activeStreams.remove(localId)
        scope.launch {
            try {
                val closeMessage = AdbMessage(
                    command = AdbMessage.A_CLSE,
                    arg0 = localId,
                    arg1 = remoteId
                )
                sendMessage(closeMessage)
            } catch (ignored: Exception) {}
        }
    }

    internal fun removeStream(localId: Int) {
        activeStreams.remove(localId)
    }

    override fun close() {
        if (!isConnected) return
        isConnected = false
        readerJob?.cancel()
        activeStreams.values.forEach { it.close() }
        activeStreams.clear()
        streamOpenCompletions.values.forEach { it.cancel() }
        streamOpenCompletions.clear()
        try {
            socket?.close()
        } catch (ignored: Exception) {}
    }
}
