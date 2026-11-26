package org.localdex.core.adb

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.localdex.core.adb.crypto.AdbCrypto
import org.localdex.core.adb.pairing.AdbPairingClient
import org.localdex.core.adb.pairing.PairingAuth
import org.localdex.core.adb.pairing.PairingProtocol
import org.localdex.core.adb.pairing.PairingResult
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class AdbPairingClientTest {

    private fun createMockClient(): AdbPairingClient {
        val mockContext = mockk<Context>(relaxed = true)
        val mockCrypto = mockk<AdbCrypto>(relaxed = true)
        every { mockCrypto.getAdbPublicKeyString() } returns "AAAAFakePublicKey localdex@localhost\u0000"
        return AdbPairingClient(mockContext, mockCrypto)
    }

    @Test
    fun pairOverTransport_matchingCode_succeedsAndExchangesPeerInfo() {
        val client = createMockClient()
        val pin = "482913"
        val exportedKeyMaterial = ByteArray(64) { (it + 7).toByte() }

        val clientToServerIn = PipedInputStream(16384)
        val clientToServerOut = PipedOutputStream(clientToServerIn)
        val serverToClientIn = PipedInputStream(16384)
        val serverToClientOut = PipedOutputStream(serverToClientIn)

        var receivedPeerInfo: ByteArray? = null

        val serverThread = thread {
            val dis = DataInputStream(clientToServerIn)
            val dos = DataOutputStream(serverToClientOut)

            val serverPassword = pin.toByteArray(StandardCharsets.US_ASCII) + exportedKeyMaterial
            val serverAuth = PairingAuth.createServer(serverPassword)

            val clientSpake2Msg = PairingProtocol.readPacket(dis, PairingProtocol.PACKET_TYPE_SPAKE2_MSG)
            PairingProtocol.writePacket(dos, PairingProtocol.PACKET_TYPE_SPAKE2_MSG, serverAuth.message)

            assertThat(serverAuth.initCipher(clientSpake2Msg)).isTrue()

            val encryptedClientPeerInfo = PairingProtocol.readPacket(dis, PairingProtocol.PACKET_TYPE_PEER_INFO)
            receivedPeerInfo = serverAuth.decrypt(encryptedClientPeerInfo)

            val serverPeerInfo = ByteArray(PairingProtocol.MAX_PEER_INFO_SIZE).apply {
                this[0] = PairingProtocol.PEER_INFO_TYPE_ADB_DEVICE_GUID
                this[1] = 'd'.code.toByte()
            }
            PairingProtocol.writePacket(dos, PairingProtocol.PACKET_TYPE_PEER_INFO, serverAuth.encrypt(serverPeerInfo))
        }

        val result = client.pairOverTransport(
            input = serverToClientIn,
            output = clientToServerOut,
            exportedKeyMaterial = exportedKeyMaterial,
            pairingCode = pin
        )
        serverThread.join(5000)

        assertThat(result).isEqualTo(PairingResult.Success)
        assertThat(receivedPeerInfo).isNotNull()
        assertThat(receivedPeerInfo!!.size).isEqualTo(PairingProtocol.MAX_PEER_INFO_SIZE)
        assertThat(receivedPeerInfo!![0]).isEqualTo(PairingProtocol.PEER_INFO_TYPE_ADB_RSA_PUB_KEY)
        val receivedKeyStr = String(receivedPeerInfo!!, 1, 39, StandardCharsets.UTF_8)
        assertThat(receivedKeyStr).startsWith("AAAAFakePublicKey localdex@localhost")
    }

    @Test
    fun pairOverTransport_wrongPin_returnsFailure() {
        val client = createMockClient()
        val exportedKeyMaterial = ByteArray(64) { 0x22.toByte() }

        val clientToServerIn = PipedInputStream(16384)
        val clientToServerOut = PipedOutputStream(clientToServerIn)
        val serverToClientIn = PipedInputStream(16384)
        val serverToClientOut = PipedOutputStream(serverToClientIn)

        val serverThread = thread {
            val dis = DataInputStream(clientToServerIn)
            val dos = DataOutputStream(serverToClientOut)

            val serverPassword = "999999".toByteArray(StandardCharsets.US_ASCII) + exportedKeyMaterial
            val serverAuth = PairingAuth.createServer(serverPassword)

            val clientSpake2Msg = PairingProtocol.readPacket(dis, PairingProtocol.PACKET_TYPE_SPAKE2_MSG)
            PairingProtocol.writePacket(dos, PairingProtocol.PACKET_TYPE_SPAKE2_MSG, serverAuth.message)

            serverAuth.initCipher(clientSpake2Msg)
            PairingProtocol.readPacket(dis, PairingProtocol.PACKET_TYPE_PEER_INFO)
            // Send dummy peer info encrypted under server's mismatched key
            val serverPeerInfo = ByteArray(PairingProtocol.MAX_PEER_INFO_SIZE)
            PairingProtocol.writePacket(dos, PairingProtocol.PACKET_TYPE_PEER_INFO, serverAuth.encrypt(serverPeerInfo))
        }

        val result = client.pairOverTransport(
            input = serverToClientIn,
            output = clientToServerOut,
            exportedKeyMaterial = exportedKeyMaterial,
            pairingCode = "111111"
        )
        serverThread.join(5000)

        assertThat(result).isInstanceOf(PairingResult.Failure::class.java)
    }
}
