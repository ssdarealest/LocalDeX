package org.localdex.core.scrcpy.decoder

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Connects to the scrcpy audio stream socket and renders low-latency PCM audio via [AudioTrack].
 * Supports OPUS, AAC, FLAC (decoded with [MediaCodec]) and RAW PCM passthrough.
 */
class AudioStreamDecoder(
    private val host: String = "127.0.0.1",
    private val port: Int,
    private val audioTrackFactory: (sampleRate: Int, channelCount: Int) -> AudioTrack = ::defaultAudioTrackFactory
) : Closeable {

    enum class CodecType(val codecId: Int, val mimeType: String) {
        OPUS(CODEC_OPUS, MediaFormat.MIMETYPE_AUDIO_OPUS),
        AAC(CODEC_AAC, MediaFormat.MIMETYPE_AUDIO_AAC),
        FLAC(CODEC_FLAC, MediaFormat.MIMETYPE_AUDIO_FLAC),
        RAW(CODEC_RAW, MediaFormat.MIMETYPE_AUDIO_RAW);

        companion object {
            fun fromCodecId(codecId: Int): CodecType? = entries.firstOrNull { it.codecId == codecId }
        }
    }

    internal data class PacketHeader(
        val isConfig: Boolean,
        val pts: Long,
        val size: Int
    )

    companion object {
        private const val TAG = "AudioStreamDecoder"
        const val CODEC_DISABLED = 0x00000000
        const val CODEC_ERROR = 0x00000001
        const val CODEC_OPUS = 0x6f707573 // "opus"
        const val CODEC_AAC = 0x00616163  // "\0aac"
        const val CODEC_FLAC = 0x666c6163 // "flac"
        const val CODEC_RAW = 0x00726177  // "\0raw"

        const val SAMPLE_RATE = 48000
        const val CHANNEL_COUNT = 2
        private const val MAX_PACKET_SIZE_BYTES = 1024 * 1024

        internal fun parsePacketHeader(ptsAndFlags: Long, packetSize: Int): PacketHeader? {
            if (packetSize <= 0 || packetSize > MAX_PACKET_SIZE_BYTES) return null
            val isConfig = (ptsAndFlags and (1L shl 63)) != 0L
            val pts = ptsAndFlags and 0x3FFFFFFFFFFFFFFFL
            return PacketHeader(isConfig = isConfig, pts = pts, size = packetSize)
        }

        internal fun buildMediaFormat(codecType: CodecType, configData: ByteArray?): MediaFormat {
            return MediaFormat.createAudioFormat(codecType.mimeType, SAMPLE_RATE, CHANNEL_COUNT).apply {
                if (configData != null && configData.isNotEmpty()) {
                    setByteBuffer("csd-0", ByteBuffer.wrap(configData.copyOf()))
                    if (codecType == CodecType.OPUS) {
                        val zeroHeader = ByteBuffer.allocate(8)
                            .order(ByteOrder.nativeOrder())
                            .putLong(0L)
                            .apply { flip() }
                        setByteBuffer("csd-1", zeroHeader.duplicate())
                        setByteBuffer("csd-2", zeroHeader.duplicate())
                    }
                }
            }
        }

        private fun defaultAudioTrackFactory(sampleRate: Int, channelCount: Int): AudioTrack {
            val channelMask = if (channelCount == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(minBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var inputStream: DataInputStream? = null
    private var mediaCodec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private var decodeJob: Job? = null

    @Volatile
    private var isRunning = false

    @Volatile
    var detectedCodec: CodecType? = null
        private set

    /**
     * Opens the audio TCP socket before control socket acceptance.
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Connecting to audio stream socket at $host:$port...")
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 5000)
            s.tcpNoDelay = true
            socket = s
            inputStream = DataInputStream(s.getInputStream())
            return@withContext true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to connect audio stream socket: ${e.message}")
            close()
            return@withContext false
        }
    }

    /**
     * Reads the 4-byte scrcpy audio codec header and starts PCM decoding after [connect].
     */
    suspend fun startStreaming(): Boolean = withContext(Dispatchers.IO) {
        try {
            val dis = inputStream ?: return@withContext false
            val codecId = dis.readInt()
            if (codecId == CODEC_DISABLED || codecId == CODEC_ERROR) {
                Log.i(TAG, "Server reported audio stream disabled/unavailable (code=$codecId)")
                close()
                return@withContext false
            }

            val codecType = CodecType.fromCodecId(codecId)
            if (codecType == null) {
                Log.w(TAG, "Unsupported audio codec ID: 0x${codecId.toUInt().toString(16)}")
                close()
                return@withContext false
            }

            detectedCodec = codecType
            Log.i(TAG, "Audio stream connected: codec=${codecType.name}")

            val track = audioTrackFactory(SAMPLE_RATE, CHANNEL_COUNT)
            track.play()
            audioTrack = track

            isRunning = true
            startDecodeLoop(dis, codecType, track)
            return@withContext true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start audio stream decoder: ${e.message}")
            close()
            return@withContext false
        }
    }

    suspend fun start(): Boolean = connect() && startStreaming()

    private fun startDecodeLoop(
        dis: DataInputStream,
        codecType: CodecType,
        initialTrack: AudioTrack
    ) {
        decodeJob = scope.launch {
            val bufferInfo = MediaCodec.BufferInfo()
            var activeTrack = initialTrack
            var currentSampleRate = SAMPLE_RATE
            var currentChannels = CHANNEL_COUNT
            var activeCodec: MediaCodec? = null

            try {
                while (isActive && isRunning) {
                    val ptsAndFlags = dis.readLong()
                    val packetSize = dis.readInt()
                    val header = parsePacketHeader(ptsAndFlags, packetSize)
                    if (header == null) {
                        Log.w(TAG, "Invalid audio packet size: $packetSize")
                        break
                    }

                    val packetData = ByteArray(header.size)
                    dis.readFully(packetData)

                    if (codecType == CodecType.RAW) {
                        activeTrack.write(packetData, 0, packetData.size, AudioTrack.WRITE_BLOCKING)
                        continue
                    }

                    if (activeCodec == null) {
                        val format = buildMediaFormat(
                            codecType = codecType,
                            configData = if (header.isConfig) packetData else null
                        )
                        val codec = MediaCodec.createDecoderByType(codecType.mimeType)
                        codec.configure(format, null, null, 0)
                        codec.start()
                        activeCodec = codec
                        mediaCodec = codec

                        if (header.isConfig) {
                            // Config data already supplied as csd-0; wait for first encoded audio frame.
                            continue
                        }
                    }

                    val inIndex = activeCodec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = activeCodec.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            inBuffer.clear()
                            inBuffer.put(packetData)
                            val flags = if (header.isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                            activeCodec.queueInputBuffer(inIndex, 0, packetData.size, header.pts, flags)
                        }
                    }

                    while (isActive && isRunning) {
                        val outIndex = activeCodec.dequeueOutputBuffer(bufferInfo, 0)
                        when {
                            outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val outFormat = activeCodec.outputFormat
                                val newSampleRate = if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                    outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                } else currentSampleRate
                                val newChannels = if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                    outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                } else currentChannels
                                if (newSampleRate != currentSampleRate || newChannels != currentChannels) {
                                    currentSampleRate = newSampleRate
                                    currentChannels = newChannels
                                    try {
                                        activeTrack.stop()
                                        activeTrack.release()
                                    } catch (_: Exception) {}
                                    activeTrack = audioTrackFactory(newSampleRate, newChannels)
                                    activeTrack.play()
                                    audioTrack = activeTrack
                                }
                            }
                            outIndex >= 0 -> {
                                if (bufferInfo.size > 0) {
                                    val outBuffer = activeCodec.getOutputBuffer(outIndex)
                                    if (outBuffer != null) {
                                        val pcm = ByteArray(bufferInfo.size)
                                        outBuffer.position(bufferInfo.offset)
                                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                        outBuffer.get(pcm)
                                        activeTrack.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                                    }
                                }
                                activeCodec.releaseOutputBuffer(outIndex, false)
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
            } catch (_: EOFException) {
                Log.i(TAG, "Audio stream ended (EOF)")
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Audio decode loop terminated: ${e.message}")
                }
            } finally {
                close()
            }
        }
    }

    override fun close() {
        isRunning = false
        decodeJob?.cancel()
        decodeJob = null

        try {
            mediaCodec?.stop()
        } catch (_: Exception) {}
        try {
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null

        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        scope.cancel()
    }
}
