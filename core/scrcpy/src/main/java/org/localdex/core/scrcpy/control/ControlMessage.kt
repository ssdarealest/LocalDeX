package org.localdex.core.scrcpy.control

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

sealed class ControlMessage {

    abstract fun toByteArray(): ByteArray

    companion object {
        const val TYPE_INJECT_KEYCODE = 0
        const val TYPE_INJECT_TEXT = 1
        const val TYPE_INJECT_TOUCH_EVENT = 2
        const val TYPE_INJECT_SCROLL_EVENT = 3
        const val TYPE_BACK_OR_SCREEN_ON = 4
        const val TYPE_EXPAND_NOTIFICATION_PANEL = 5
        const val TYPE_COLLAPSE_PANELS = 6
        const val TYPE_GET_CLIPBOARD = 7
        const val TYPE_SET_CLIPBOARD = 8
        const val TYPE_SET_SCREEN_POWER_MODE = 9
        const val TYPE_ROTATE_DEVICE = 10

        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2

        const val BUTTON_PRIMARY = 1
        const val BUTTON_SECONDARY = 2
        const val BUTTON_TERTIARY = 4
    }

    /**
     * Injects a physical/virtual key event.
     */
    data class Keycode(
        val action: Int,
        val keycode: Int,
        val repeat: Int = 0,
        val metaState: Int = 0
    ) : ControlMessage() {
        override fun toByteArray(): ByteArray {
            val buffer = ByteBuffer.allocate(14)
            buffer.put(TYPE_INJECT_KEYCODE.toByte())
            buffer.put(action.toByte())
            buffer.putInt(keycode)
            buffer.putInt(repeat)
            buffer.putInt(metaState)
            return buffer.array()
        }
    }

    /**
     * Injects text directly via scrcpy.
     */
    data class Text(val text: String) : ControlMessage() {
        override fun toByteArray(): ByteArray {
            val textBytes = text.toByteArray(StandardCharsets.UTF_8)
            val buffer = ByteBuffer.allocate(5 + textBytes.size)
            buffer.put(TYPE_INJECT_TEXT.toByte())
            buffer.putInt(textBytes.size)
            buffer.put(textBytes)
            return buffer.array()
        }
    }

    /**
     * Injects touch / pointer event targeting the virtual display resolution.
     */
    data class Touch(
        val action: Int,
        val pointerId: Long,
        val x: Int,
        val y: Int,
        val displayWidth: Int,
        val displayHeight: Int,
        val pressure: Float = 1.0f,
        val actionButton: Int = 0,
        val buttons: Int = 0
    ) : ControlMessage() {
        override fun toByteArray(): ByteArray {
            val buffer = ByteBuffer.allocate(32)
            buffer.put(TYPE_INJECT_TOUCH_EVENT.toByte())
            buffer.put(action.toByte())
            buffer.putLong(pointerId)
            buffer.putInt(x)
            buffer.putInt(y)
            buffer.putShort(displayWidth.toShort())
            buffer.putShort(displayHeight.toShort())
            val normalizedPressure = (pressure.coerceIn(0f, 1f) * 0xFFFF).toInt().toShort()
            buffer.putShort(normalizedPressure)
            buffer.putInt(actionButton)
            buffer.putInt(buttons)
            return buffer.array()
        }
    }

    /**
     * Injects mouse wheel / trackpad scroll event (21-byte scrcpy v3.1 fixed-point format).
     */
    data class Scroll(
        val x: Int,
        val y: Int,
        val displayWidth: Int,
        val displayHeight: Int,
        val hScroll: Float,
        val vScroll: Float,
        val buttons: Int = 0
    ) : ControlMessage() {
        override fun toByteArray(): ByteArray {
            val buffer = ByteBuffer.allocate(21)
            buffer.put(TYPE_INJECT_SCROLL_EVENT.toByte())
            buffer.putInt(x)
            buffer.putInt(y)
            buffer.putShort(displayWidth.toShort())
            buffer.putShort(displayHeight.toShort())
            buffer.putShort(toFixedPoint16(hScroll))
            buffer.putShort(toFixedPoint16(vScroll))
            buffer.putInt(buttons)
            return buffer.array()
        }

        private fun toFixedPoint16(value: Float): Short {
            val clamped = value.coerceIn(-1f, 1f)
            return (clamped * 32768f)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    /**
     * Forwards Android Back gesture / button.
     */
    data class BackOrScreenOn(val action: Int) : ControlMessage() {
        override fun toByteArray(): ByteArray {
            val buffer = ByteBuffer.allocate(2)
            buffer.put(TYPE_BACK_OR_SCREEN_ON.toByte())
            buffer.put(action.toByte())
            return buffer.array()
        }
    }
}
