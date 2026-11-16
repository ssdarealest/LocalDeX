package org.localdex.core.adb.protocol

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-level ADB binary packet definition.
 * 24-byte header:
 * [command: 4B, arg0: 4B, arg1: 4B, data_length: 4B, data_check: 4B, magic: 4B] + [payload]
 */
data class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray = ByteArray(0)
) {
    val dataLength: Int get() = data.size

    val dataCheck: Int get() {
        var sum = 0
        for (b in data) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }

    val magic: Int get() = command xor -0x1 // command ^ 0xFFFFFFFF

    fun writeTo(output: OutputStream) {
        val header = ByteBuffer.allocate(HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(dataLength)
        header.putInt(dataCheck)
        header.putInt(magic)

        output.write(header.array())
        if (data.isNotEmpty()) {
            output.write(data)
        }
        output.flush()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdbMessage
        if (command != other.command) return false
        if (arg0 != other.arg0) return false
        if (arg1 != other.arg1) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = command
        result = 31 * result + arg0
        result = 31 * result + arg1
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_LENGTH = 24

        // ADB Protocol Commands
        const val A_SYNC = 0x434e5953 // "SYNC"
        const val A_CNXN = 0x4e584e43 // "CNXN"
        const val A_AUTH = 0x48545541 // "AUTH"
        const val A_OPEN = 0x4e45504f // "OPEN"
        const val A_OKAY = 0x59414b4f // "OKAY"
        const val A_CLSE = 0x45534c43 // "CLSE"
        const val A_WRTE = 0x45545257 // "WRTE"

        // ADB AUTH Sub-types
        const val AUTH_TYPE_TOKEN = 1
        const val AUTH_TYPE_SIGNATURE = 2
        const val AUTH_TYPE_RSA_PUBLIC = 3

        // ADB Version & Max Payload
        const val ADB_VERSION = 0x01000000 // 1.0.0
        const val MAX_PAYLOAD = 1024 * 1024 // 1 MB

        fun readFrom(input: InputStream): AdbMessage {
            val headerBytes = ByteArray(HEADER_LENGTH)
            var read = 0
            while (read < HEADER_LENGTH) {
                val r = input.read(headerBytes, read, HEADER_LENGTH - read)
                if (r == -1) throw java.io.EOFException("Premature EOF reading ADB header")
                read += r
            }

            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val command = buffer.int
            val arg0 = buffer.int
            val arg1 = buffer.int
            val length = buffer.int
            val check = buffer.int
            val magic = buffer.int

            if ((command xor magic) != -0x1) {
                throw IllegalStateException("Corrupt ADB packet: magic mismatch (cmd: $command, magic: $magic)")
            }

            val payload = if (length > 0) {
                if (length > MAX_PAYLOAD) {
                    throw IllegalStateException("Payload size exceeds maximum: $length")
                }
                val data = ByteArray(length)
                var dataRead = 0
                while (dataRead < length) {
                    val r = input.read(data, dataRead, length - dataRead)
                    if (r == -1) throw java.io.EOFException("Premature EOF reading ADB payload")
                    dataRead += r
                }
                data
            } else {
                ByteArray(0)
            }

            return AdbMessage(command, arg0, arg1, payload)
        }
    }
}
