package org.localdex.core.scrcpy.control

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Sends serialized control messages to the scrcpy server over loopback socket.
 */
class ControlSocketWriter(
    private val host: String = "127.0.0.1",
    private val port: Int
) : Closeable {

    companion object {
        private const val TAG = "ControlSocketWriter"
    }

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private val sendChannel = Channel<ControlMessage>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var writerJob: Job? = null

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Connecting to control socket at $host:$port...")
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 5000)
            s.tcpNoDelay = true
            socket = s
            outputStream = s.getOutputStream()

            writerJob = scope.launch {
                try {
                    for (msg in sendChannel) {
                        if (!isActive) break
                        val bytes = msg.toByteArray()
                        outputStream?.write(bytes)
                        outputStream?.flush()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Writer loop terminated: ${e.message}")
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to control socket", e)
            return@withContext false
        }
    }

    /**
     * Enqueues a control message for immediate delivery.
     */
    fun send(message: ControlMessage) {
        sendChannel.trySend(message)
    }

    override fun close() {
        sendChannel.close()
        writerJob?.cancel()
        try {
            socket?.close()
        } catch (ignored: Exception) {}
    }
}
