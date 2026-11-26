package org.localdex.core.adb

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.localdex.core.adb.connection.AdbConnection
import org.localdex.core.adb.crypto.AdbCrypto
import org.localdex.core.adb.protocol.AdbMessage
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AdbConnectionTest {

    private fun mockCrypto(): AdbCrypto = mockk<AdbCrypto>(relaxed = true).apply {
        every { signAdbToken(any()) } answers {
            val token = firstArg<ByteArray>()
            byteArrayOf(0x5a) + token
        }
        every { getAdbPublicKeyString() } returns "AAAA_TEST_PUBKEY localdex@localhost\u0000"
    }

    @Test
    fun connect_handlesAuthChallengeAndExecutesShellAndPushFile() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        val crypto = mockCrypto()
        val capturedSyncBytes = mutableListOf<Byte>()
        val sawCloseForRejectedStream = AtomicBoolean(false)

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverThread = thread {
                server.accept().use { client ->
                    val input = client.getInputStream()
                    val output = client.getOutputStream()

                    // 1. Expect CNXN
                    val cnxn = AdbMessage.readFrom(input)
                    assertThat(cnxn.command).isEqualTo(AdbMessage.A_CNXN)

                    // 2. Send AUTH(TOKEN) -> expect SIGNATURE -> reply AUTH -> expect RSA_PUBLIC -> reply CNXN
                    AdbMessage(AdbMessage.A_AUTH, AdbMessage.AUTH_TYPE_TOKEN, 0, byteArrayOf(1, 2, 3, 4)).writeTo(output)
                    val sig = AdbMessage.readFrom(input)
                    assertThat(sig.command).isEqualTo(AdbMessage.A_AUTH)
                    assertThat(sig.arg0).isEqualTo(AdbMessage.AUTH_TYPE_SIGNATURE)

                    AdbMessage(AdbMessage.A_AUTH, AdbMessage.AUTH_TYPE_TOKEN, 0, byteArrayOf(5, 6, 7, 8)).writeTo(output)
                    val pub = AdbMessage.readFrom(input)
                    assertThat(pub.command).isEqualTo(AdbMessage.A_AUTH)
                    assertThat(pub.arg0).isEqualTo(AdbMessage.AUTH_TYPE_RSA_PUBLIC)
                    assertThat(String(pub.data, Charsets.UTF_8)).contains("AAAA_TEST_PUBKEY")

                    AdbMessage(AdbMessage.A_CNXN, AdbMessage.ADB_VERSION, AdbMessage.MAX_PAYLOAD, "device::test\u0000".toByteArray())
                        .writeTo(output)

                    // 3. Handle stream OPEN requests
                    while (!client.isClosed) {
                        val req = try {
                            AdbMessage.readFrom(input)
                        } catch (_: Exception) {
                            break
                        }
                        when (req.command) {
                            AdbMessage.A_OPEN -> {
                                val localId = req.arg0
                                val dest = String(req.data, Charsets.UTF_8).trimEnd('\u0000')
                                when {
                                    dest == "shell:echo ok" -> {
                                        AdbMessage(AdbMessage.A_OKAY, 101, localId).writeTo(output)
                                        AdbMessage(AdbMessage.A_WRTE, 101, localId, "ok\n".toByteArray()).writeTo(output)
                                        val ack = AdbMessage.readFrom(input)
                                        assertThat(ack.command).isEqualTo(AdbMessage.A_OKAY)
                                        AdbMessage(AdbMessage.A_CLSE, 101, localId).writeTo(output)
                                    }
                                    dest == "localabstract:not_ready" -> {
                                        sawCloseForRejectedStream.set(true)
                                        AdbMessage(AdbMessage.A_CLSE, 0, localId).writeTo(output)
                                    }
                                    dest == "sync:" -> {
                                        AdbMessage(AdbMessage.A_OKAY, 202, localId).writeTo(output)
                                        // Read SEND + DATA + DONE packets until DONE arrives
                                        while (true) {
                                            val pkt = AdbMessage.readFrom(input)
                                            if (pkt.command == AdbMessage.A_CLSE) break
                                            if (pkt.command == AdbMessage.A_WRTE) {
                                                AdbMessage(AdbMessage.A_OKAY, 202, localId).writeTo(output)
                                                pkt.data.forEach { capturedSyncBytes.add(it) }
                                                val s = String(pkt.data, Charsets.US_ASCII)
                                                if (s.startsWith("DONE")) {
                                                    val okayResp = ByteBuffer.allocate(8)
                                                        .order(ByteOrder.LITTLE_ENDIAN)
                                                        .put("OKAY".toByteArray())
                                                        .putInt(0)
                                                        .array()
                                                    AdbMessage(AdbMessage.A_WRTE, 202, localId, okayResp).writeTo(output)
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val conn = AdbConnection(
                context = context,
                port = server.localPort,
                useTls = false,
                adbCrypto = crypto
            )

            try {
                assertThat(conn.connect()).isTrue()

                val shellOut = conn.executeShell("echo ok")
                assertThat(shellOut).isEqualTo("ok\n")

                var openFailed = false
                try {
                    conn.openStream("localabstract:not_ready")
                } catch (_: IOException) {
                    openFailed = true
                }
                assertThat(sawCloseForRejectedStream.get()).isTrue()
                assertThat(openFailed).isTrue()

                val payload = "hello-scrcpy".toByteArray()
                val pushed = conn.pushFile(payload, "/data/local/tmp/test.bin", permissionsMode = 493)
                assertThat(pushed).isTrue()
                val syncStr = String(capturedSyncBytes.toByteArray(), Charsets.US_ASCII)
                assertThat(syncStr).contains("SEND")
                assertThat(syncStr).contains("/data/local/tmp/test.bin,493")
                assertThat(syncStr).contains("DATA")
                assertThat(syncStr).contains("hello-scrcpy")
                assertThat(syncStr).contains("DONE")
            } finally {
                conn.close()
                serverThread.join(2000)
            }
        }
    }
}
