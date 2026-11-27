package org.localdex.core.adb.pairing

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire structures matching AOSP `adb/pairing_connection/pairing_connection.cpp`
 * and `pairing_connection.h`.
 */
object PairingProtocol {
    const val HEADER_VERSION: Byte = 1
    const val HEADER_SIZE_BYTES = 6
    const val MAX_PEER_INFO_SIZE = 8192
    const val MAX_PAYLOAD_SIZE = MAX_PEER_INFO_SIZE * 2

    const val PACKET_TYPE_SPAKE2_MSG: Byte = 0
    const val PACKET_TYPE_PEER_INFO: Byte = 1

    const val PEER_INFO_TYPE_ADB_RSA_PUB_KEY: Byte = 0
    const val PEER_INFO_TYPE_ADB_DEVICE_GUID: Byte = 1

    data class PacketHeader(
        val version: Byte,
        val type: Byte,
        val payloadSize: Int
    ) {
        fun toByteArray(): ByteArray {
            return ByteBuffer.allocate(HEADER_SIZE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .put(version)
                .put(type)
                .putInt(payloadSize)
                .array()
        }

        companion object {
            fun readFrom(input: DataInputStream): PacketHeader {
                val version = input.readByte()
                if (version != HEADER_VERSION) {
                    throw IllegalStateException("Unsupported pairing header version: $version")
                }
                val type = input.readByte()
                if (type != PACKET_TYPE_SPAKE2_MSG && type != PACKET_TYPE_PEER_INFO) {
                    throw IllegalStateException("Unknown pairing packet type: $type")
                }
                val payloadSize = input.readInt()
                if (payloadSize <= 0 || payloadSize > MAX_PAYLOAD_SIZE) {
                    throw IllegalStateException("Unsafe pairing payload size: $payloadSize")
                }
                return PacketHeader(version, type, payloadSize)
            }
        }
    }

    fun writePacket(output: DataOutputStream, type: Byte, payload: ByteArray) {
        val header = PacketHeader(HEADER_VERSION, type, payload.size)
        output.write(header.toByteArray())
        output.write(payload)
        output.flush()
    }

    fun readPacket(input: DataInputStream, expectedType: Byte): ByteArray {
        val header = PacketHeader.readFrom(input)
        if (header.type != expectedType) {
            throw IllegalStateException(
                "Unexpected pairing packet type: expected $expectedType, got ${header.type}"
            )
        }
        val payload = ByteArray(header.payloadSize)
        input.readFully(payload)
        return payload
    }

    /**
     * Builds the fixed 8192-byte `PeerInfo` struct: `[type: 1B, data: 8191B]`.
     */
    fun buildPeerInfoRsaKey(adbPublicKeyBytes: ByteArray): ByteArray {
        require(adbPublicKeyBytes.size <= MAX_PEER_INFO_SIZE - 1) {
            "ADB public key exceeds PeerInfo data capacity (${adbPublicKeyBytes.size} bytes)"
        }
        val peerInfo = ByteArray(MAX_PEER_INFO_SIZE)
        peerInfo[0] = PEER_INFO_TYPE_ADB_RSA_PUB_KEY
        System.arraycopy(adbPublicKeyBytes, 0, peerInfo, 1, adbPublicKeyBytes.size)
        return peerInfo
    }
}
