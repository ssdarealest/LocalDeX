package org.localdex.core.scrcpy

import android.media.AudioTrack
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.localdex.core.scrcpy.decoder.AudioStreamDecoder
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class AudioStreamDecoderTest {

    @Test
    fun codecType_fromCodecId_mapsAllScrcpyAudioCodecs() {
        assertThat(AudioStreamDecoder.CodecType.fromCodecId(AudioStreamDecoder.CODEC_OPUS))
            .isEqualTo(AudioStreamDecoder.CodecType.OPUS)
        assertThat(AudioStreamDecoder.CodecType.fromCodecId(AudioStreamDecoder.CODEC_AAC))
            .isEqualTo(AudioStreamDecoder.CodecType.AAC)
        assertThat(AudioStreamDecoder.CodecType.fromCodecId(AudioStreamDecoder.CODEC_FLAC))
            .isEqualTo(AudioStreamDecoder.CodecType.FLAC)
        assertThat(AudioStreamDecoder.CodecType.fromCodecId(AudioStreamDecoder.CODEC_RAW))
            .isEqualTo(AudioStreamDecoder.CodecType.RAW)
        assertThat(AudioStreamDecoder.CodecType.fromCodecId(0x12345678)).isNull()
    }

    @Test
    fun parsePacketHeader_extractsConfigFlagPtsAndValidatesSize() {
        val ptsWithConfig = (1L shl 63) or 123_456L
        val configHeader = AudioStreamDecoder.parsePacketHeader(ptsWithConfig, 19)
        assertThat(configHeader).isNotNull()
        assertThat(configHeader!!.isConfig).isTrue()
        assertThat(configHeader.pts).isEqualTo(123_456L)
        assertThat(configHeader.size).isEqualTo(19)

        val mediaHeader = AudioStreamDecoder.parsePacketHeader(987_654L, 512)
        assertThat(mediaHeader!!.isConfig).isFalse()
        assertThat(mediaHeader.pts).isEqualTo(987_654L)

        assertThat(AudioStreamDecoder.parsePacketHeader(0L, 0)).isNull()
        assertThat(AudioStreamDecoder.parsePacketHeader(0L, -1)).isNull()
        assertThat(AudioStreamDecoder.parsePacketHeader(0L, 2 * 1024 * 1024)).isNull()
    }

    @Test
    fun start_returnsFalseWhenServerDisablesAudioStream() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val acceptThread = thread {
                server.accept().use { client ->
                    DataOutputStream(client.getOutputStream()).use { out ->
                        out.writeInt(AudioStreamDecoder.CODEC_DISABLED)
                        out.flush()
                    }
                }
            }

            val decoder = AudioStreamDecoder(port = server.localPort)
            val started = decoder.start()
            assertThat(started).isFalse()
            assertThat(decoder.detectedCodec).isNull()
            acceptThread.join(2000)
            decoder.close()
        }
    }

    @Test
    fun start_streamsRawPcmFramesToAudioTrack() = runBlocking {
        val mockTrack = mockk<AudioTrack>(relaxed = true)
        val written = CompletableDeferred<ByteArray>()
        every { mockTrack.write(any<ByteArray>(), any(), any(), any()) } answers {
            val buf = firstArg<ByteArray>()
            val offset = secondArg<Int>()
            val size = thirdArg<Int>()
            written.complete(buf.copyOfRange(offset, offset + size))
            size
        }

        val expectedPcm = byteArrayOf(0x01, 0x02, 0x7f, -0x80, 0x10, 0x20, 0x30, 0x40)

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val acceptThread = thread {
                server.accept().use { client ->
                    DataOutputStream(client.getOutputStream()).use { out ->
                        out.writeInt(AudioStreamDecoder.CODEC_RAW)
                        out.writeLong(42_000L)
                        out.writeInt(expectedPcm.size)
                        out.write(expectedPcm)
                        out.flush()
                    }
                }
            }

            val decoder = AudioStreamDecoder(
                port = server.localPort,
                audioTrackFactory = { _, _ -> mockTrack }
            )
            try {
                assertThat(decoder.start()).isTrue()
                assertThat(decoder.detectedCodec).isEqualTo(AudioStreamDecoder.CodecType.RAW)

                val captured = withTimeout(3000) { written.await() }
                assertThat(captured).isEqualTo(expectedPcm)
                verify { mockTrack.play() }
            } finally {
                decoder.close()
                acceptThread.join(2000)
            }
        }
    }
}
