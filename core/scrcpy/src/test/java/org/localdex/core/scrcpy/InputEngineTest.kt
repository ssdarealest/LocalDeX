package org.localdex.core.scrcpy

import android.view.KeyEvent
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.localdex.core.scrcpy.control.ControlMessage
import org.localdex.core.scrcpy.control.ControlSocketWriter
import org.localdex.core.scrcpy.input.InputEngine
import org.localdex.core.scrcpy.input.InputMode

class InputEngineTest {

    private lateinit var mockWriter: ControlSocketWriter
    private lateinit var inputEngine: InputEngine

    @Before
    fun setUp() {
        mockWriter = mockk(relaxed = true)
        inputEngine = InputEngine(
            writer = mockWriter,
            displayWidth = 1920,
            displayHeight = 1080,
            inputMode = InputMode.DIRECT_TOUCH
        )
    }

    @Test
    fun directTouch_scalesCoordinatesAndSendsTouchDown() {
        val event = mockk<MotionEvent>()
        every { event.getToolType(0) } returns MotionEvent.TOOL_TYPE_FINGER
        every { event.pointerCount } returns 1
        every { event.getPointerId(0) } returns 0
        every { event.getX(0) } returns 480f
        every { event.getY(0) } returns 270f
        every { event.getPressure(0) } returns 0.75f
        every { event.actionMasked } returns MotionEvent.ACTION_DOWN

        val handled = inputEngine.onTouchEvent(event, viewWidth = 960, viewHeight = 540)
        assertThat(handled).isTrue()

        val slot = slot<ControlMessage.Touch>()
        verify { mockWriter.send(capture(slot)) }

        val touch = slot.captured
        assertThat(touch.action).isEqualTo(ControlMessage.ACTION_DOWN)
        assertThat(touch.x).isEqualTo(960) // 480 * (1920 / 960)
        assertThat(touch.y).isEqualTo(540) // 270 * (1080 / 540)
        assertThat(touch.pressure).isEqualTo(0.75f)
    }

    @Test
    fun stylusTouch_routesDirectlyWithPressureAndPrimaryButton() {
        val event = mockk<MotionEvent>()
        every { event.getToolType(0) } returns MotionEvent.TOOL_TYPE_STYLUS
        every { event.x } returns 960f
        every { event.y } returns 540f
        every { event.getPressure(0) } returns 0.9f
        every { event.actionMasked } returns MotionEvent.ACTION_DOWN
        every { event.buttonState } returns 0

        val handled = inputEngine.onTouchEvent(event, viewWidth = 1920, viewHeight = 1080)
        assertThat(handled).isTrue()

        val slot = slot<ControlMessage.Touch>()
        verify { mockWriter.send(capture(slot)) }

        val touch = slot.captured
        assertThat(touch.action).isEqualTo(ControlMessage.ACTION_DOWN)
        assertThat(touch.buttons).isEqualTo(ControlMessage.BUTTON_PRIMARY)
        assertThat(touch.pressure).isEqualTo(0.9f)
    }

    @Test
    fun stylusHover_sendsActionMoveWithZeroButtons() {
        val event = mockk<MotionEvent>()
        every { event.getToolType(0) } returns MotionEvent.TOOL_TYPE_STYLUS
        every { event.x } returns 500f
        every { event.y } returns 300f
        every { event.getPressure(0) } returns 0.0f
        every { event.actionMasked } returns MotionEvent.ACTION_HOVER_MOVE
        every { event.buttonState } returns 0

        val handled = inputEngine.onGenericMotionEvent(event, viewWidth = 1920, viewHeight = 1080)
        assertThat(handled).isTrue()

        val slot = slot<ControlMessage.Touch>()
        verify { mockWriter.send(capture(slot)) }

        val touch = slot.captured
        assertThat(touch.action).isEqualTo(ControlMessage.ACTION_MOVE)
        assertThat(touch.buttons).isEqualTo(0)
    }

    @Test
    fun stylusEraser_sendsSecondaryButton() {
        val event = mockk<MotionEvent>()
        every { event.getToolType(0) } returns MotionEvent.TOOL_TYPE_ERASER
        every { event.x } returns 600f
        every { event.y } returns 400f
        every { event.getPressure(0) } returns 0.8f
        every { event.actionMasked } returns MotionEvent.ACTION_DOWN
        every { event.buttonState } returns 0

        val handled = inputEngine.onTouchEvent(event, viewWidth = 1920, viewHeight = 1080)
        assertThat(handled).isTrue()

        val slot = slot<ControlMessage.Touch>()
        verify { mockWriter.send(capture(slot)) }

        assertThat(slot.captured.buttons).isEqualTo(ControlMessage.BUTTON_SECONDARY)
    }

    @Test
    fun hardwareMouse_relativePointerCapture_accumulatesCursorPosition() {
        val initialX = inputEngine.cursorX
        val initialY = inputEngine.cursorY

        val event = mockk<MotionEvent>()
        every { event.getToolType(0) } returns MotionEvent.TOOL_TYPE_MOUSE
        every { event.actionMasked } returns MotionEvent.ACTION_MOVE
        every { event.getAxisValue(MotionEvent.AXIS_RELATIVE_X) } returns 25f
        every { event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) } returns -15f
        every { event.buttonState } returns MotionEvent.BUTTON_PRIMARY

        val handled = inputEngine.handleHardwareMouseEvent(
            event = event,
            viewWidth = 1920,
            viewHeight = 1080,
            isPointerCaptured = true
        )
        assertThat(handled).isTrue()

        assertThat(inputEngine.cursorX).isEqualTo(initialX + 25f)
        assertThat(inputEngine.cursorY).isEqualTo(initialY - 15f)

        val slot = slot<ControlMessage.Touch>()
        verify { mockWriter.send(capture(slot)) }

        val touch = slot.captured
        assertThat(touch.x).isEqualTo((initialX + 25f).toInt())
        assertThat(touch.y).isEqualTo((initialY - 15f).toInt())
        assertThat(touch.buttons).isEqualTo(ControlMessage.BUTTON_PRIMARY)
    }

    @Test
    fun hardwareMouse_scrollEvent_sendsScrollMessage() {
        val event = mockk<MotionEvent>()
        every { event.getToolType(0) } returns MotionEvent.TOOL_TYPE_MOUSE
        every { event.actionMasked } returns MotionEvent.ACTION_SCROLL
        every { event.x } returns 960f
        every { event.y } returns 540f
        every { event.getAxisValue(MotionEvent.AXIS_VSCROLL) } returns -2.0f
        every { event.getAxisValue(MotionEvent.AXIS_HSCROLL) } returns 1.0f
        every { event.buttonState } returns 0

        val handled = inputEngine.onGenericMotionEvent(
            event = event,
            viewWidth = 1920,
            viewHeight = 1080,
            isPointerCaptured = false
        )
        assertThat(handled).isTrue()

        val slot = slot<ControlMessage.Scroll>()
        verify { mockWriter.send(capture(slot)) }

        val scroll = slot.captured
        assertThat(scroll.vScroll).isEqualTo(-2.0f)
        assertThat(scroll.hScroll).isEqualTo(1.0f)
    }

    @Test
    fun keyEvent_sendsKeycodeMessage() {
        val event = mockk<KeyEvent>()
        every { event.action } returns KeyEvent.ACTION_DOWN
        every { event.keyCode } returns KeyEvent.KEYCODE_ENTER
        every { event.repeatCount } returns 0
        every { event.metaState } returns KeyEvent.META_SHIFT_ON

        val handled = inputEngine.onKeyEvent(event)
        assertThat(handled).isTrue()

        val slot = slot<ControlMessage.Keycode>()
        verify { mockWriter.send(capture(slot)) }

        val keycode = slot.captured
        assertThat(keycode.action).isEqualTo(ControlMessage.ACTION_DOWN)
        assertThat(keycode.keycode).isEqualTo(KeyEvent.KEYCODE_ENTER)
        assertThat(keycode.metaState).isEqualTo(KeyEvent.META_SHIFT_ON)
    }

    @Test
    fun backGesture_sendsDownAndUpBackMessages() {
        inputEngine.sendBackGesture()

        verify(exactly = 1) {
            mockWriter.send(ControlMessage.BackOrScreenOn(ControlMessage.ACTION_DOWN))
        }
        verify(exactly = 1) {
            mockWriter.send(ControlMessage.BackOrScreenOn(ControlMessage.ACTION_UP))
        }
    }
}
