package org.localdex.core.scrcpy

import android.view.Surface
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.localdex.core.scrcpy.decoder.VideoStreamDecoder
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class VideoStreamDecoderTest {

    @Test
    fun connect_consumesDummyByteBeforeHeaderWhenEnabled() = runBlocking {
        val surface = mockk<Surface>(relaxed = true)
        val header = ByteArray(64).apply { "Galaxy".toByteArray().copyInto(this) }

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverThread = thread {
                server.accept().use { client ->
                    client.getOutputStream().apply {
                        write(0x00) // dummy byte
                        write(header)
                        flush()
                    }
                    Thread.sleep(150)
                }
            }

            val decoder = VideoStreamDecoder(port = server.localPort, surface = surface, readDummyByte = true)
            try {
                assertThat(decoder.connect()).isTrue()
            } finally {
                decoder.close()
                serverThread.join(2000)
            }
        }
    }

    @Test
    fun connect_returnsFalseWhenDummyByteStreamClosesImmediately() = runBlocking {
        val surface = mockk<Surface>(relaxed = true)

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverThread = thread {
                server.accept().close()
            }

            val decoder = VideoStreamDecoder(port = server.localPort, surface = surface, readDummyByte = true)
            assertThat(decoder.connect()).isFalse()
            serverThread.join(2000)
        }
    }

    @Test
    fun connect_preservesHeaderBytesWhenReadDummyByteDisabled() = runBlocking {
        val surface = mockk<Surface>(relaxed = true)
        val deviceHeader = "LocalDeX".toByteArray()

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverThread = thread {
                server.accept().use { client ->
                    client.getOutputStream().apply {
                        write(deviceHeader)
                        flush()
                    }
                    Thread.sleep(150)
                }
            }

            val decoder = VideoStreamDecoder(port = server.localPort, surface = surface, readDummyByte = false)
            try {
                assertThat(decoder.connect()).isTrue()
                // Validate internal stream still sits at 'L' using reflection check on socket input
                val field = VideoStreamDecoder::class.java.getDeclaredField("inputStream").apply {
                    isAccessible = true
                }
                val dis = field.get(decoder) as DataInputStream
                val readHeader = ByteArray(deviceHeader.size)
                dis.readFully(readHeader)
                assertThat(readHeader).isEqualTo(deviceHeader)
            } finally {
                decoder.close()
                serverThread.join(2000)
            }
        }
    }
}
