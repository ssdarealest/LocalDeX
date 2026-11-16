package org.localdex.core.adb.connection

import kotlinx.coroutines.channels.Channel
import org.localdex.core.adb.protocol.AdbMessage
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Represents a single logical multiplexed ADB stream (e.g. `shell:...` or `sync:`).
 */
class AdbStream(
    val localId: Int,
    val connection: AdbConnection,
    val destination: String
) : Closeable {

    var remoteId: Int = 0
        internal set

    private val isClosed = AtomicBoolean(false)
    val readChannel = Channel<ByteArray>(Channel.UNLIMITED)

    /**
     * Sends data payload across this stream to adbd.
     */
    suspend fun write(data: ByteArray) {
        if (isClosed.get()) throw IllegalStateException("Cannot write to closed ADB stream $localId")
        val message = AdbMessage(
            command = AdbMessage.A_WRTE,
            arg0 = localId,
            arg1 = remoteId,
            data = data
        )
        connection.sendMessage(message)
    }

    /**
     * Reads all incoming output until the stream is closed by adbd.
     */
    suspend fun readFullyToString(): String {
        val stringBuilder = StringBuilder()
        for (chunk in readChannel) {
            stringBuilder.append(String(chunk, Charsets.UTF_8))
        }
        return stringBuilder.toString()
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            readChannel.close()
            connection.closeStream(this)
        }
    }

    internal fun onClosedByRemote() {
        if (isClosed.compareAndSet(false, true)) {
            readChannel.close()
            connection.removeStream(localId)
        }
    }
}
