package org.localdex.core.scrcpy

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.localdex.core.scrcpy.control.ControlMessage
import org.localdex.core.scrcpy.control.ControlSocketWriter
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class ControlSocketWriterTest {

    @Test
    fun connectAndSend_writesControlMessagesInOrderOverLoopbackSocket() = runBlocking {
        val msg1 = ControlMessage.Keycode(ControlMessage.ACTION_DOWN, KeyEvent.KEYCODE_HOME)
        val msg2 = ControlMessage.Scroll(100, 200, 1920, 1080, hScroll = 0.5f, vScroll = -0.5f)
        val expectedBytes = msg1.toByteArray() + msg2.toByteArray()

        val received = CompletableDeferred<ByteArray>()

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val readerThread = thread {
                server.accept().use { client ->
                    val buf = ByteArray(expectedBytes.size)
                    DataInputStream(client.getInputStream()).readFully(buf)
                    received.complete(buf)
                }
            }

            val writer = ControlSocketWriter(port = server.localPort)
            try {
                assertThat(writer.connect()).isTrue()
                writer.send(msg1)
                writer.send(msg2)

                val actualBytes = withTimeout(3000) { received.await() }
                assertThat(actualBytes).isEqualTo(expectedBytes)
            } finally {
                writer.close()
                readerThread.join(2000)
            }
        }
    }

    @Test
    fun connect_returnsFalseWhenPortUnreachable() = runBlocking {
        val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val writer = ControlSocketWriter(port = closedPort)
        assertThat(writer.connect()).isFalse()
        writer.close()
    }
}
