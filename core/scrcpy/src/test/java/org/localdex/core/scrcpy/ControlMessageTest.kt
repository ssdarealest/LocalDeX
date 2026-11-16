package org.localdex.core.scrcpy

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.localdex.core.scrcpy.control.ControlMessage
import java.nio.ByteBuffer

class ControlMessageTest {

    @Test
    fun keycodeMessage_serializationMatchesSpec() {
        val msg = ControlMessage.Keycode(
            action = ControlMessage.ACTION_DOWN,
            keycode = 4, // KEYCODE_BACK
            repeat = 0,
            metaState = 0
        )
        val bytes = msg.toByteArray()
        assertThat(bytes.size).isEqualTo(14)

        val buffer = ByteBuffer.wrap(bytes)
        assertThat(buffer.get()).isEqualTo(ControlMessage.TYPE_INJECT_KEYCODE.toByte())
        assertThat(buffer.get()).isEqualTo(ControlMessage.ACTION_DOWN.toByte())
        assertThat(buffer.int).isEqualTo(4)
        assertThat(buffer.int).isEqualTo(0)
        assertThat(buffer.int).isEqualTo(0)
    }

    @Test
    fun touchMessage_serializationNormalizesCoordinatesAndPressure() {
        val msg = ControlMessage.Touch(
            action = ControlMessage.ACTION_MOVE,
            pointerId = 1L,
            x = 960,
            y = 540,
            displayWidth = 1920,
            displayHeight = 1080,
            pressure = 1.0f,
            buttons = ControlMessage.BUTTON_PRIMARY
        )
        val bytes = msg.toByteArray()
        assertThat(bytes.size).isEqualTo(32)

        val buffer = ByteBuffer.wrap(bytes)
        assertThat(buffer.get()).isEqualTo(ControlMessage.TYPE_INJECT_TOUCH_EVENT.toByte())
        assertThat(buffer.get()).isEqualTo(ControlMessage.ACTION_MOVE.toByte())
        assertThat(buffer.long).isEqualTo(1L)
        assertThat(buffer.int).isEqualTo(960)
        assertThat(buffer.int).isEqualTo(540)
        assertThat(buffer.short).isEqualTo(1920.toShort())
        assertThat(buffer.short).isEqualTo(1080.toShort())
        assertThat(buffer.short).isEqualTo((-1).toShort()) // 0xFFFF signed
    }

    @Test
    fun textMessage_serializationPutsLengthAndPayload() {
        val text = "LocalDeX"
        val msg = ControlMessage.Text(text)
        val bytes = msg.toByteArray()

        val buffer = ByteBuffer.wrap(bytes)
        assertThat(buffer.get()).isEqualTo(ControlMessage.TYPE_INJECT_TEXT.toByte())
        assertThat(buffer.int).isEqualTo(text.length)
        val payload = ByteArray(text.length)
        buffer.get(payload)
        assertThat(String(payload)).isEqualTo(text)
    }
}
