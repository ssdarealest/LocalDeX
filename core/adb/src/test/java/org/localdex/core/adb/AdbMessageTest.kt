package org.localdex.core.adb

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.localdex.core.adb.protocol.AdbMessage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AdbMessageTest {

    @Test
    fun serializeAndDeserialize_preservesPayloadAndHeaders() {
        val payload = "shell:getprop ro.build.version.release\u0000".toByteArray(Charsets.UTF_8)
        val originalMessage = AdbMessage(
            command = AdbMessage.A_OPEN,
            arg0 = 100,
            arg1 = 0,
            data = payload
        )

        val outputStream = ByteArrayOutputStream()
        originalMessage.writeTo(outputStream)

        val serializedBytes = outputStream.toByteArray()
        assertThat(serializedBytes.size).isEqualTo(AdbMessage.HEADER_LENGTH + payload.size)

        val inputStream = ByteArrayInputStream(serializedBytes)
        val deserializedMessage = AdbMessage.readFrom(inputStream)

        assertThat(deserializedMessage.command).isEqualTo(AdbMessage.A_OPEN)
        assertThat(deserializedMessage.arg0).isEqualTo(100)
        assertThat(deserializedMessage.arg1).isEqualTo(0)
        assertThat(deserializedMessage.data).isEqualTo(payload)
        assertThat(deserializedMessage.magic).isEqualTo(originalMessage.magic)
        assertThat(deserializedMessage.dataCheck).isEqualTo(originalMessage.dataCheck)
    }

    @Test
    fun magicCalculation_isBitwiseInversionOfCommand() {
        val cnxn = AdbMessage(command = AdbMessage.A_CNXN, arg0 = 0, arg1 = 0)
        assertThat(cnxn.magic).isEqualTo(AdbMessage.A_CNXN xor -0x1)

        val wrte = AdbMessage(command = AdbMessage.A_WRTE, arg0 = 1, arg1 = 2)
        assertThat(wrte.magic).isEqualTo(AdbMessage.A_WRTE xor -0x1)
    }

    @Test(expected = IllegalStateException::class)
    fun corruptPacket_magicMismatch_throwsIllegalStateException() {
        val corruptBytes = ByteArray(AdbMessage.HEADER_LENGTH)
        // Set command = A_CNXN but magic = 0
        java.nio.ByteBuffer.wrap(corruptBytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(AdbMessage.A_CNXN)
            .putInt(0)
            .putInt(0)
            .putInt(0)
            .putInt(0)
            .putInt(0) // Invalid magic

        AdbMessage.readFrom(ByteArrayInputStream(corruptBytes))
    }
}
