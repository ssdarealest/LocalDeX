package org.localdex.core.scrcpy.server

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.localdex.core.adb.connection.AdbConnection
import org.localdex.core.adb.connection.AdbStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridges local TCP connections on `127.0.0.1:<ephemeralPort>` to an ADB UNIX abstract
 * socket stream (e.g. `localabstract:scrcpy`) over [AdbConnection].
 *
 * This replaces `adb forward` on-device without requiring `adb` CLI or external hosts.
 */
class ScrcpyLoopbackBridge(
    private val connection: AdbConnection,
    private val socketDestination: String = "localabstract:scrcpy",
    private val connectRetryAttempts: Int = 20,
    private val connectRetryDelayMs: Long = 100L
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val activeSockets = Collections.synchronizedSet(mutableSetOf<Socket>())
    private val activeStreams = Collections.synchronizedSet(mutableSetOf<AdbStream>())

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptJob: Job? = null

    @Volatile
    var localPort: Int = -1
        private set

    companion object {
        private const val TAG = "ScrcpyLoopbackBridge"
        private const val WRITE_CHUNK_BYTES = 32 * 1024
    }

    /**
     * Starts listening on an ephemeral loopback port and accepting client connections.
     *
     * @return The bound `127.0.0.1` TCP port.
     */
    @Synchronized
    fun start(): Int {
        check(!closed.get()) { "ScrcpyLoopbackBridge has already been closed" }
        serverSocket?.let { return localPort }

        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        localPort = socket.localPort
        Log.i(TAG, "Loopback bridge listening on 127.0.0.1:$localPort -> $socketDestination")

        acceptJob = scope.launch {
            acceptLoop(socket)
        }
        return localPort
    }

    private suspend fun acceptLoop(listener: ServerSocket) {
        while (scope.isActive && !closed.get() && !listener.isClosed) {
            val client = try {
                listener.accept()
            } catch (e: Exception) {
                if (!closed.get() && !listener.isClosed) {
                    Log.w(TAG, "Loopback accept failed: ${e.message}")
                }
                break
            }

            activeSockets.add(client)
            val adbStream = try {
                client.tcpNoDelay = true
                openStreamWithRetry()
            } catch (e: CancellationException) {
                activeSockets.remove(client)
                try {
                    client.close()
                } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                if (!closed.get()) {
                    Log.w(TAG, "Bridge connection to $socketDestination failed: ${e.message}")
                }
                activeSockets.remove(client)
                try {
                    client.close()
                } catch (_: Exception) {}
                continue
            }

            activeStreams.add(adbStream)
            scope.launch {
                pumpClient(client, adbStream)
            }
        }
    }

    private suspend fun openStreamWithRetry(): AdbStream {
        var lastError: Exception? = null
        val attempts = connectRetryAttempts.coerceAtLeast(1)
        for (attempt in 1..attempts) {
            if (closed.get()) {
                throw IllegalStateException("Bridge closed before stream opened")
            }
            try {
                return connection.openStream(socketDestination)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < attempts && connectRetryDelayMs > 0L) {
                    delay(connectRetryDelayMs)
                }
            }
        }
        throw lastError ?: IllegalStateException("Failed to open ADB stream $socketDestination")
    }

    private suspend fun pumpClient(client: Socket, stream: AdbStream) {
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            coroutineScope {
                val remoteToLocal = launch {
                    try {
                        for (chunk in stream.readChannel) {
                            output.write(chunk)
                            output.flush()
                        }
                    } catch (_: CancellationException) {
                    } catch (e: Exception) {
                        if (!closed.get() && !client.isClosed) {
                            Log.d(TAG, "ADB->TCP pump ended: ${e.message}")
                        }
                    } finally {
                        try {
                            client.close()
                        } catch (_: Exception) {
                        }
                    }
                }

                val localToRemote = launch {
                    val buffer = ByteArray(WRITE_CHUNK_BYTES)
                    try {
                        while (isActive && !closed.get() && !client.isClosed) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) break
                            if (bytesRead > 0) {
                                stream.write(buffer.copyOf(bytesRead))
                            }
                        }
                    } catch (_: CancellationException) {
                    } catch (e: Exception) {
                        if (!closed.get() && !client.isClosed) {
                            Log.d(TAG, "TCP->ADB pump ended: ${e.message}")
                        }
                    } finally {
                        remoteToLocal.cancel()
                        try {
                            stream.close()
                        } catch (_: Exception) {
                        }
                    }
                }

                remoteToLocal.join()
                localToRemote.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!closed.get()) {
                Log.w(TAG, "Bridge connection to $socketDestination failed: ${e.message}")
            }
        } finally {
            activeStreams.remove(stream)
            try {
                stream.close()
            } catch (_: Exception) {
            }
            activeSockets.remove(client)
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null

        val socketsSnapshot = synchronized(activeSockets) { activeSockets.toList() }
        socketsSnapshot.forEach { socket ->
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
        activeSockets.clear()

        val streamsSnapshot = synchronized(activeStreams) { activeStreams.toList() }
        streamsSnapshot.forEach { stream ->
            try {
                stream.close()
            } catch (_: Exception) {
            }
        }
        activeStreams.clear()

        acceptJob?.cancel()
        acceptJob = null
        scope.cancel()
    }
}
