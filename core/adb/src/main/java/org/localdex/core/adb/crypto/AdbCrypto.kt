package org.localdex.core.adb.crypto

import android.content.Context
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Date

/**
 * Handles cryptographic key generation, certificate creation, and ADB signature verification
 * for loopback TLS and legacy ADB authentication.
 */
class AdbCrypto private constructor(
    val keyPair: KeyPair,
    val certificate: X509Certificate
) {
    val privateKey: PrivateKey get() = keyPair.private
    val publicKey: RSAPublicKey get() = keyPair.public as RSAPublicKey

    /**
     * Signs an ADB AUTH token challenge using RSA SHA-1 (standard ADB wire protocol).
     */
    fun signAdbToken(token: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initSign(privateKey)
        signature.update(token)
        return signature.sign()
    }

    /**
     * Formats the public key into the ADB `adbkey.pub` format expected by adbd:
     * Base64 encoded RSAPublicKey structure followed by comment and null terminator.
     */
    fun getAdbPublicKeyString(comment: String = "localdex@localhost"): String {
        val pubKeyBytes = encodeAdbPublicKey(publicKey)
        val base64 = Base64.encodeToString(pubKeyBytes, Base64.NO_WRAP)
        return "$base64 $comment\u0000"
    }

    companion object {
        private const val KEY_FILE_NAME = "localdex_adb_key"
        private const val CERT_FILE_NAME = "localdex_adb_cert"
        private const val KEY_SIZE = 2048

        /**
         * Loads existing ADB cryptographic credentials or generates a fresh RSA-2048 keypair
         * with a self-signed X.509 certificate for TLS 1.3 pairing.
         */
        @Synchronized
        fun getOrCreate(context: Context): AdbCrypto {
            val keyFile = File(context.filesDir, KEY_FILE_NAME)
            val certFile = File(context.filesDir, CERT_FILE_NAME)

            if (keyFile.exists() && certFile.exists()) {
                try {
                    val keyBytes = keyFile.readBytes()
                    val keySpec = PKCS8EncodedKeySpec(keyBytes)
                    val kf = KeyFactory.getInstance("RSA")
                    val privKey = kf.generatePrivate(keySpec) as RSAPrivateCrtKey

                    val pubSpec = RSAPublicKeySpec(privKey.modulus, privKey.publicExponent)
                    val pubKey = kf.generatePublic(pubSpec)
                    val keyPair = KeyPair(pubKey, privKey)

                    val certBytes = certFile.readBytes()
                    val cf = java.security.cert.CertificateFactory.getInstance("X.509")
                    val cert = cf.generateCertificate(certBytes.inputStream()) as X509Certificate

                    return AdbCrypto(keyPair, cert)
                } catch (e: Exception) {
                    keyFile.delete()
                    certFile.delete()
                }
            }

            // Generate fresh RSA 2048 keypair
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(KEY_SIZE)
            val keyPair = kpg.generateKeyPair()

            // Generate self-signed certificate valid for 10 years
            val cert = generateSelfSignedCertificate(keyPair)

            // Persist securely in app private storage
            keyFile.writeBytes(keyPair.private.encoded)
            certFile.writeBytes(cert.encoded)

            return AdbCrypto(keyPair, cert)
        }

        private fun generateSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
            val now = System.currentTimeMillis()
            val notBefore = Date(now - 24 * 60 * 60 * 1000L)
            val notAfter = Date(now + 10L * 365 * 24 * 60 * 60 * 1000L)
            val serial = BigInteger.valueOf(now)
            val subject = X500Name("CN=LocalDeX, O=LocalDeX, C=US")

            val contentSigner = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
            val certBuilder = JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.public
            )

            return JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner))
        }

        /**
         * Encodes RSAPublicKey into standard Android ADB structure:
         * [modulus_len(4), n0inv(4), modulus(256), rr(256), exponent(4)]
         */
        private fun encodeAdbPublicKey(pubKey: RSAPublicKey): ByteArray {
            val n = pubKey.modulus
            val r = BigInteger.ONE.shiftLeft(KEY_SIZE)
            val rr = r.multiply(r).mod(n)
            val r32 = BigInteger.ONE.shiftLeft(32)
            val n0inv = n.modInverse(r32).negate().and(r32.subtract(BigInteger.ONE)).toLong()

            val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(KEY_SIZE / 32) // 64 words
            buffer.putInt(n0inv.toInt())

            val nBytes = getPaddedBytes(n, 256)
            buffer.put(nBytes)

            val rrBytes = getPaddedBytes(rr, 256)
            buffer.put(rrBytes)

            buffer.putInt(pubKey.publicExponent.toInt())
            return buffer.array()
        }

        private fun getPaddedBytes(bigInt: BigInteger, length: Int): ByteArray {
            val src = bigInt.toByteArray()
            val dest = ByteArray(length)
            var srcOffset = 0
            var copyLen = src.size
            if (src.isNotEmpty() && src[0] == 0.toByte()) {
                srcOffset = 1
                copyLen--
            }
            if (copyLen > length) {
                copyLen = length
            }
            // ADB expects little-endian order for the big numbers
            for (i in 0 until copyLen) {
                dest[i] = src[src.size - 1 - i]
            }
            return dest
        }
    }
}
