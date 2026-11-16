package org.localdex.core.scrcpy.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Connects to scrcpy video socket and decodes H.264/H.265 frames onto a Surface with low latency.
 */
class VideoStreamDecoder(
    private val host: String = "127.0.0.1",
    private val port: Int,
    private val surface: Surface
) : Closeable {

    companion object {
        private const val TAG = "VideoStreamDecoder"
        private const val CODEC_H264 = 0x68323634 // "h264"
        private const val CODEC_H265 = 0x68323635 // "h265"
    }

    private var socket: Socket? = null
    private var mediaCodec: MediaCodec? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var decodeJob: Job? = null
    private var isRunning = false

    var videoWidth = 0
        private set
    var videoHeight = 0
        private set

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Connecting to video stream socket at $host:$port...")
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 5000)
            s.tcpNoDelay = true
            socket = s

            val dis = DataInputStream(s.getInputStream())

            // 1. Read 64-byte device name header
            val deviceNameBytes = ByteArray(64)
            dis.readFully(deviceNameBytes)
            val deviceName = String(deviceNameBytes, Charsets.UTF_8).trim { it <= ' ' }
            Log.i(TAG, "Scrcpy stream connected: device '$deviceName'")

            // 2. Read codec ID (4 bytes)
            val codecId = dis.readInt()
            val mimeType = when (codecId) {
                CODEC_H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
                else -> MediaFormat.MIMETYPE_VIDEO_AVC
            }

            // 3. Read initial dimensions (4 bytes width, 4 bytes height)
            videoWidth = dis.readInt()
            videoHeight = dis.readInt()
            Log.i(TAG, "Stream format: $mimeType, dimensions: ${videoWidth}x${videoHeight}")

            // 4. Configure low-latency MediaCodec
            val format = MediaFormat.createVideoFormat(mimeType, videoWidth, videoHeight).apply {
                // Low latency decoding flags
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
                // Qualcomm / Exynos low-latency extensions
                try {
                    setInteger("vendor.qcom-ext-dec-low-latency.enable", 1)
                } catch (ignored: Exception) {}
            }

            val codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(format, surface, null, 0)
            codec.start()
            mediaCodec = codec

            isRunning = true
            startDemuxAndDecodeLoop(dis, codec)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video stream decoder", e)
            close()
            return@withContext false
        }
    }

    private fun startDemuxAndDecodeLoop(dis: DataInputStream, codec: MediaCodec) {
        decodeJob = scope.launch {
            val bufferInfo = MediaCodec.BufferInfo()

            try {
                while (isActive && isRunning) {
                    // 1. Read packet header (8 bytes PTS, 4 bytes packet size)
                    val ptsAndFlags = dis.readLong()
                    val packetSize = dis.readInt()

                    if (packetSize <= 0 || packetSize > 10 * 1024 * 1024) {
                        Log.w(TAG, "Invalid packet size: $packetSize")
                        continue
                    }

                    val isConfig = (ptsAndFlags and (1L shl 63)) != 0L
                    val isKeyFrame = (ptsAndFlags and (1L shl 62)) != 0L
                    val pts = ptsAndFlags and 0x3FFFFFFFFFFFFFFFL

                    // 2. Read packet payload
                    val packetData = ByteArray(packetSize)
                    dis.readFully(packetData)

                    // 3. Queue to MediaCodec
                    val inIndex = codec.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(packetData)
                            val flags = when {
                                isConfig -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                                isKeyFrame -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                                else -> 0
                            }
                            codec.queueInputBuffer(inIndex, 0, packetSize, pts, flags)
                        }
                    }

                    // 4. Release output buffers to Surface with low latency
                    var outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    while (outIndex >= 0) {
                        codec.releaseOutputBuffer(outIndex, true) // Render immediately
                        outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Decode loop terminated: ${e.message}")
                }
            } finally {
                close()
            }
        }
    }

    override fun close() {
        if (!isRunning) return
        isRunning = false
        decodeJob?.cancel()
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (ignored: Exception) {}
        mediaCodec = null

        try {
            socket?.close()
        } catch (ignored: Exception) {}
        socket = null
    }
}
