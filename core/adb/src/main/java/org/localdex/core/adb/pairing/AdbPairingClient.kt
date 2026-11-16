package org.localdex.core.adb.pairing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.localdex.core.adb.crypto.AdbCrypto
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed class PairingResult {
    data object Success : PairingResult()
    data class Failure(val message: String, val throwable: Throwable? = null) : PairingResult()
}

/**
 * Executes the Android 11+ Wireless Debugging TLS pairing exchange over localhost.
 */
class AdbPairingClient(
    private val context: Context,
    private val adbCrypto: AdbCrypto = AdbCrypto.getOrCreate(context)
) {
    companion object {
        private const val TAG = "AdbPairingClient"
        private const val PAIRING_TIMEOUT_MS = 15000
    }

    /**
     * Attempts to pair with the local ADB daemon using the 6-digit PIN and discovered pairing port.
     */
    suspend fun pair(host: String = "127.0.0.1", port: Int, pairingCode: String): PairingResult = withContext(Dispatchers.IO) {
        val cleanCode = pairingCode.trim()
        if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
            return@withContext PairingResult.Failure("Pairing code must be exactly 6 digits")
        }

        var socket: Socket? = null
        try {
            Log.i(TAG, "Starting pairing connection to $host:$port with code: $cleanCode")

            // 1. Build an SSLContext that presents our client certificate
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry("client", adbCrypto.privateKey, null, arrayOf(adbCrypto.certificate))
            }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, null)
            }

            // Trust all certificates during initial pairing handshake
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            })

            val sslContext = SSLContext.getInstance("TLSv1.3").apply {
                init(kmf.keyManagers, trustAllCerts, java.security.SecureRandom())
            }

            // 2. Establish raw TCP connection then wrap with TLS
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(host, port), PAIRING_TIMEOUT_MS)
            rawSocket.soTimeout = PAIRING_TIMEOUT_MS

            val sslSocket = (sslContext.socketFactory.createSocket(
                rawSocket,
                host,
                port,
                true
            ) as SSLSocket).apply {
                useClientMode = true
                enabledProtocols = arrayOf("TLSv1.3")
                startHandshake()
            }
            socket = sslSocket

            // 3. Complete ADB pairing protocol frame exchange
            val inputStream = DataInputStream(sslSocket.inputStream)
            val outputStream = DataOutputStream(sslSocket.outputStream)

            // The ADB pairing packet format:
            // Type (1 byte) + Length (4 bytes BE) + Payload (Password / Certificate exchange)
            val codeBytes = cleanCode.toByteArray(StandardCharsets.UTF_8)
            val packet = ByteArray(1 + 4 + codeBytes.size)
            packet[0] = 1.toByte() // PAIRING_PACKET_TYPE_PASSWD
            val lenBuffer = java.nio.ByteBuffer.allocate(4).putInt(codeBytes.size).array()
            System.arraycopy(lenBuffer, 0, packet, 1, 4)
            System.arraycopy(codeBytes, 0, packet, 5, codeBytes.size)

            outputStream.write(packet)
            outputStream.flush()

            // Read response
            val responseType = inputStream.readByte()
            val responseLength = inputStream.readInt()
            val responseData = ByteArray(responseLength.coerceAtLeast(0))
            if (responseLength > 0) {
                inputStream.readFully(responseData)
            }

            Log.i(TAG, "Pairing server responded with type: $responseType, len: $responseLength")
            return@withContext PairingResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Pairing handshake failed", e)
            return@withContext PairingResult.Failure("Pairing failed: ${e.localizedMessage}", e)
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {}
        }
    }
}
