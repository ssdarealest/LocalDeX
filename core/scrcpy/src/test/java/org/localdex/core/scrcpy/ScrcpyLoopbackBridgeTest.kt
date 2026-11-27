package org.localdex.core.scrcpy

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.localdex.core.adb.connection.AdbConnection
import org.localdex.core.adb.connection.AdbStream
import org.localdex.core.scrcpy.server.ScrcpyLoopbackBridge
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.Socket
import java.util.Collections

class ScrcpyLoopbackBridgeTest {

    @Test
    fun bridge_forwardsBidirectionalTrafficBetweenLoopbackSocketAndAdbStream() = runBlocking {
        val connection = mockk<AdbConnection>()
        val incoming = Channel<ByteArray>(Channel.UNLIMITED)
        val writtenChunks = Collections.synchronizedList(mutableListOf<ByteArray>())
        var totalBytesWritten = 0
        val receivedAll = CompletableDeferred<Unit>()
        val streamClosed = CompletableDeferred<Unit>()

        val mockStream = mockk<AdbStream>(relaxed = true)
        every { mockStream.readChannel } returns incoming
        coEvery { mockStream.write(any()) } answers {
            val chunk = firstArg<ByteArray>()
            synchronized(writtenChunks) {
                writtenChunks.add(chunk)
                totalBytesWritten += chunk.size
                if (totalBytesWritten >= 40_000) {
                    receivedAll.complete(Unit)
                }
            }
        }
        every { mockStream.close() } answers {
            incoming.close()
            streamClosed.complete(Unit)
        }

        coEvery { connection.openStream("localabstract:scrcpy") } returns mockStream

        val bridge = ScrcpyLoopbackBridge(
            connection = connection,
            socketDestination = "localabstract:scrcpy",
            connectRetryAttempts = 1,
            connectRetryDelayMs = 0L
        )

        try {
            val port = bridge.start()
            assertThat(port).isGreaterThan(0)

            // Queue dummy byte + 6-byte payload from remote scrcpy-server
            incoming.trySend(byteArrayOf(0x00))
            incoming.trySend("SCRCPY".toByteArray(Charsets.UTF_8))

            Socket("127.0.0.1", port).use { socket ->
                val dis = DataInputStream(socket.getInputStream())
                val dummy = dis.readByte()
                assertThat(dummy).isEqualTo(0.toByte())

                val header = ByteArray(6)
                dis.readFully(header)
                assertThat(String(header, Charsets.UTF_8)).isEqualTo("SCRCPY")

                val largePayload = ByteArray(40_000) { (it % 251).toByte() }
                socket.getOutputStream().write(largePayload)
                socket.getOutputStream().flush()

                withTimeout(3000) {
                    receivedAll.await()
                }
                incoming.close()
                withTimeout(3000) {
                    streamClosed.await()
                }

                val collected = ByteArrayOutputStream()
                synchronized(writtenChunks) {
                    assertThat(writtenChunks.maxOf { it.size }).isAtMost(32 * 1024)
                    writtenChunks.forEach { collected.write(it) }
                }
                assertThat(collected.toByteArray()).isEqualTo(largePayload)
            }
        } finally {
            bridge.close()
        }
    }

    @Test
    fun bridge_retriesOpeningAdbStreamUntilServerIsListening() = runBlocking {
        val connection = mockk<AdbConnection>()
        val incoming = Channel<ByteArray>(Channel.UNLIMITED)
        val mockStream = mockk<AdbStream>(relaxed = true)
        every { mockStream.readChannel } returns incoming
        every { mockStream.close() } answers { incoming.close() }

        var attempts = 0
        coEvery { connection.openStream("localabstract:scrcpy_0000002a") } answers {
            attempts++
            if (attempts < 3) {
                throw IllegalStateException("Listener not ready yet")
            }
            mockStream
        }

        val bridge = ScrcpyLoopbackBridge(
            connection = connection,
            socketDestination = "localabstract:scrcpy_0000002a",
            connectRetryAttempts = 5,
            connectRetryDelayMs = 10L
        )

        try {
            val port = bridge.start()
            incoming.trySend(byteArrayOf(0x00))

            Socket("127.0.0.1", port).use { socket ->
                val b = socket.getInputStream().read()
                assertThat(b).isEqualTo(0)
            }
            assertThat(attempts).isEqualTo(3)
        } finally {
            bridge.close()
        }
    }
}
