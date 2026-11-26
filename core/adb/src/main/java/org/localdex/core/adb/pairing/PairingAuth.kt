package org.localdex.core.adb.pairing

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Mirrors AOSP `adb/pairing_auth/pairing_auth.cpp` and `aes_128_gcm.cpp`.
 * Manages SPAKE25519 handshake state, HKDF-SHA256 key derivation, and sequenced AES-128-GCM encryption.
 */
class PairingAuth private constructor(
    private val role: Spake25519.Role,
    password: ByteArray
) {
    enum class PeerRole {
        CLIENT,
        SERVER
    }

    companion object {
        // Note: AOSP `sizeof(kClientName)` / `sizeof(kServerName)` includes the trailing NUL byte.
        private val CLIENT_NAME = "adb pair client\u0000".toByteArray(StandardCharsets.US_ASCII)
        private val SERVER_NAME = "adb pair server\u0000".toByteArray(StandardCharsets.US_ASCII)

        // AOSP `aes_128_gcm.cpp`: `sizeof(info) - 1` excludes the trailing NUL byte.
        private val HKDF_INFO = "adb pairing_auth aes-128-gcm key".toByteArray(StandardCharsets.US_ASCII)
        private const val AES_KEY_SIZE_BYTES = 16
        private const val GCM_NONCE_SIZE_BYTES = 12
        private const val GCM_TAG_BITS = 128

        fun createClient(password: ByteArray): PairingAuth =
            PairingAuth(Spake25519.Role.ALICE, password)

        fun createServer(password: ByteArray): PairingAuth =
            PairingAuth(Spake25519.Role.BOB, password)
    }

    private val spake2: Spake25519 = if (role == Spake25519.Role.ALICE) {
        Spake25519(Spake25519.Role.ALICE, CLIENT_NAME, SERVER_NAME)
    } else {
        Spake25519(Spake25519.Role.BOB, SERVER_NAME, CLIENT_NAME)
    }

    val message: ByteArray = spake2.generateMessage(password)

    private var aesKey: SecretKeySpec? = null
    private var encryptSequence: Long = 0L
    private var decryptSequence: Long = 0L

    /**
     * Completes SPAKE2 with [theirMessage] and initializes the AES-128-GCM session key.
     */
    fun initCipher(theirMessage: ByteArray): Boolean {
        if (aesKey != null || theirMessage.isEmpty()) return false
        val sharedKeyMaterial = spake2.processMessage(theirMessage) ?: return false

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sharedKeyMaterial, null, HKDF_INFO))
        val keyBytes = ByteArray(AES_KEY_SIZE_BYTES)
        hkdf.generateBytes(keyBytes, 0, keyBytes.size)
        aesKey = SecretKeySpec(keyBytes, "AES")
        return true
    }

    /**
     * Encrypts [plaintext] with AES-128-GCM using the incrementing little-endian sequence nonce.
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val key = checkNotNull(aesKey) { "Cipher not initialized; call initCipher first" }
        val nonce = buildSequenceNonce(encryptSequence)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        encryptSequence++
        return ciphertext
    }

    /**
     * Decrypts [ciphertext] with AES-128-GCM using the incrementing little-endian decryption nonce.
     */
    fun decrypt(ciphertext: ByteArray): ByteArray? {
        val key = checkNotNull(aesKey) { "Cipher not initialized; call initCipher first" }
        val nonce = buildSequenceNonce(decryptSequence)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            val plaintext = cipher.doFinal(ciphertext)
            decryptSequence++
            plaintext
        } catch (e: Exception) {
            null
        }
    }

    private fun buildSequenceNonce(sequence: Long): ByteArray {
        return ByteBuffer.allocate(GCM_NONCE_SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(sequence)
            .array()
    }
}
