package org.localdex.core.adb.pairing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.conscrypt.Conscrypt
import org.localdex.core.adb.crypto.AdbCrypto
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
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
 * Executes the Android 11+ Wireless Debugging TLS 1.3 + SPAKE2 pairing exchange over localhost.
 */
class AdbPairingClient(
    private val context: Context,
    private val adbCrypto: AdbCrypto = AdbCrypto.getOrCreate(context)
) {
    companion object {
        private const val TAG = "AdbPairingClient"
        private const val PAIRING_TIMEOUT_MS = 15000
        private const val EXPORTED_KEY_LABEL = "adb-label\u0000"
        private const val EXPORTED_KEY_SIZE = 64

        private val conscryptProvider by lazy {
            Conscrypt.newProviderBuilder()
                .provideTrustManager(false)
                .defaultTlsProtocol("TLSv1.3")
                .build()
        }
    }

    /**
     * Attempts to pair with the local ADB daemon using the 6-digit PIN and discovered pairing port.
     */
    suspend fun pair(host: String = "127.0.0.1", port: Int, pairingCode: String): PairingResult =
        withContext(Dispatchers.IO) {
            val cleanCode = pairingCode.trim()
            if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
                return@withContext PairingResult.Failure("Pairing code must be exactly 6 digits")
            }

            var socket: Socket? = null
            try {
                Log.i(TAG, "Starting TLS 1.3 SPAKE2 pairing to $host:$port")

                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setKeyEntry("client", adbCrypto.privateKey, null, arrayOf(adbCrypto.certificate))
                }
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                    init(keyStore, null)
                }

                // Trust adbd's ephemeral pairing certificate; authenticity is proven by SPAKE2 + PIN
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                })

                val sslContext = SSLContext.getInstance("TLSv1.3", conscryptProvider).apply {
                    init(kmf.keyManagers, trustAllCerts, SecureRandom())
                }

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

                val exportedKeyMaterial = Conscrypt.exportKeyingMaterial(
                    sslSocket,
                    EXPORTED_KEY_LABEL,
                    null,
                    EXPORTED_KEY_SIZE
                ) ?: return@withContext PairingResult.Failure("Failed to export TLS keying material")

                return@withContext pairOverTransport(
                    input = sslSocket.inputStream,
                    output = sslSocket.outputStream,
                    exportedKeyMaterial = exportedKeyMaterial,
                    pairingCode = cleanCode
                )
            } catch (e: Exception) {
                Log.e(TAG, "Pairing handshake failed", e)
                return@withContext PairingResult.Failure(
                    "Pairing failed: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    e
                )
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
            }
        }

    /**
     * Executes the SPAKE2 message exchange and encrypted `PeerInfo` verification over established streams.
     */
    internal fun pairOverTransport(
        input: InputStream,
        output: OutputStream,
        exportedKeyMaterial: ByteArray,
        pairingCode: String
    ): PairingResult {
        val cleanCode = pairingCode.trim()
        if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
            return PairingResult.Failure("Pairing code must be exactly 6 digits")
        }
        if (exportedKeyMaterial.size != EXPORTED_KEY_SIZE) {
            return PairingResult.Failure("Invalid exported TLS keying material size (${exportedKeyMaterial.size})")
        }

        val dis = DataInputStream(input)
        val dos = DataOutputStream(output)

        // Password = 6-digit PIN ASCII || 64-byte exported TLS keying material
        val pinBytes = cleanCode.toByteArray(StandardCharsets.US_ASCII)
        val password = ByteArray(pinBytes.size + exportedKeyMaterial.size)
        System.arraycopy(pinBytes, 0, password, 0, pinBytes.size)
        System.arraycopy(exportedKeyMaterial, 0, password, pinBytes.size, exportedKeyMaterial.size)

        val auth = PairingAuth.createClient(password)

        // 1. Exchange SPAKE2_MSG
        PairingProtocol.writePacket(dos, PairingProtocol.PACKET_TYPE_SPAKE2_MSG, auth.message)
        val theirSpake2Msg = PairingProtocol.readPacket(dis, PairingProtocol.PACKET_TYPE_SPAKE2_MSG)
        if (!auth.initCipher(theirSpake2Msg)) {
            return PairingResult.Failure("SPAKE2 verification failed")
        }

        // 2. Exchange AES-128-GCM encrypted PeerInfo (8192 bytes)
        val pubKeyBytes = adbCrypto.getAdbPublicKeyString().toByteArray(StandardCharsets.UTF_8)
        val ourPeerInfo = PairingProtocol.buildPeerInfoRsaKey(pubKeyBytes)
        val encryptedOurPeerInfo = auth.encrypt(ourPeerInfo)
        PairingProtocol.writePacket(dos, PairingProtocol.PACKET_TYPE_PEER_INFO, encryptedOurPeerInfo)

        val encryptedTheirPeerInfo = PairingProtocol.readPacket(dis, PairingProtocol.PACKET_TYPE_PEER_INFO)
        val theirPeerInfo = auth.decrypt(encryptedTheirPeerInfo)
            ?: return PairingResult.Failure("Incorrect pairing code or peer verification failed")

        if (theirPeerInfo.size != PairingProtocol.MAX_PEER_INFO_SIZE) {
            return PairingResult.Failure("Unexpected PeerInfo size: ${theirPeerInfo.size}")
        }

        Log.i(TAG, "Pairing completed; peer info type=${theirPeerInfo[0].toInt() and 0xFF}")
        return PairingResult.Success
    }
}
