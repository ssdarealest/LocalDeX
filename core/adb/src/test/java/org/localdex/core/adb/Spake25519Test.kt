package org.localdex.core.adb

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.localdex.core.adb.pairing.PairingAuth
import org.localdex.core.adb.pairing.Spake25519
import java.nio.charset.StandardCharsets

class Spake25519Test {

    @Test
    fun spake25519_matchingPasswordsAndNames_produceIdentical64ByteKey() {
        val password = "123456".toByteArray(StandardCharsets.UTF_8) + ByteArray(64) { it.toByte() }
        val aliceName = "adb pair client\u0000".toByteArray(StandardCharsets.US_ASCII)
        val bobName = "adb pair server\u0000".toByteArray(StandardCharsets.US_ASCII)

        val alice = Spake25519(Spake25519.Role.ALICE, aliceName, bobName)
        val bob = Spake25519(Spake25519.Role.BOB, bobName, aliceName)

        val aliceMsg = alice.generateMessage(password)
        val bobMsg = bob.generateMessage(password)

        assertThat(aliceMsg.size).isEqualTo(32)
        assertThat(bobMsg.size).isEqualTo(32)

        val aliceKey = alice.processMessage(bobMsg)
        val bobKey = bob.processMessage(aliceMsg)

        assertThat(aliceKey).isNotNull()
        assertThat(bobKey).isNotNull()
        assertThat(aliceKey!!.size).isEqualTo(64)
        assertThat(aliceKey).isEqualTo(bobKey)
    }

    @Test
    fun spake25519_mismatchedPassword_producesDifferentKeys() {
        val aliceName = "alice".toByteArray()
        val bobName = "bob".toByteArray()

        val alice = Spake25519(Spake25519.Role.ALICE, aliceName, bobName)
        val bob = Spake25519(Spake25519.Role.BOB, bobName, aliceName)

        val aliceMsg = alice.generateMessage("password-a".toByteArray())
        val bobMsg = bob.generateMessage("password-b".toByteArray())

        val aliceKey = alice.processMessage(bobMsg)
        val bobKey = bob.processMessage(aliceMsg)

        assertThat(aliceKey).isNotNull()
        assertThat(bobKey).isNotNull()
        assertThat(aliceKey).isNotEqualTo(bobKey)
    }

    @Test
    fun spake25519_mismatchedNames_producesDifferentKeys() {
        val password = "shared-password".toByteArray()

        val alice = Spake25519(Spake25519.Role.ALICE, "alice".toByteArray(), "charlie".toByteArray())
        val bob = Spake25519(Spake25519.Role.BOB, "bob".toByteArray(), "alice".toByteArray())

        val aliceMsg = alice.generateMessage(password)
        val bobMsg = bob.generateMessage(password)

        val aliceKey = alice.processMessage(bobMsg)
        val bobKey = bob.processMessage(aliceMsg)

        assertThat(aliceKey).isNotEqualTo(bobKey)
    }

    @Test
    fun spake25519_invalidPeerPoint_returnsNull() {
        val alice = Spake25519(Spake25519.Role.ALICE, "alice".toByteArray(), "bob".toByteArray())
        alice.generateMessage("password".toByteArray())

        // Wrong length
        assertThat(alice.processMessage(ByteArray(31))).isNull()

        // Non-canonical y >= p (all 0xFF with clear sign bit is 2^255 - 1 >= 2^255 - 19)
        val nonCanonical = ByteArray(32) { 0xFF.toByte() }
        nonCanonical[31] = 0x7F.toByte()
        assertThat(alice.processMessage(nonCanonical)).isNull()
    }

    @Test
    fun pairingAuth_encryptAndDecryptRoundTripAcrossMultipleMessages() {
        val password = "654321".toByteArray() + ByteArray(64) { (it * 3).toByte() }
        val client = PairingAuth.createClient(password)
        val server = PairingAuth.createServer(password)

        assertThat(client.initCipher(server.message)).isTrue()
        assertThat(server.initCipher(client.message)).isTrue()

        val payload1 = ByteArray(8192) { (it % 251).toByte() }
        val payload2 = "second-message".toByteArray()

        val enc1 = client.encrypt(payload1)
        val enc2 = client.encrypt(payload2)

        assertThat(server.decrypt(enc1)).isEqualTo(payload1)
        assertThat(server.decrypt(enc2)).isEqualTo(payload2)

        // Out-of-order decryption fails because sequence nonces must match
        assertThat(server.decrypt(enc1)).isNull()
    }
}
