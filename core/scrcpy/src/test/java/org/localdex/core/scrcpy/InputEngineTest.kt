package org.localdex.core.scrcpy

import android.view.InputDevice
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
        val event = mockk<MotionEvent>(relaxed = true)
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
        val event = mockk<MotionEvent>(relaxed = true)
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
        val event = mockk<MotionEvent>(relaxed = true)
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
        val event = mockk<MotionEvent>(relaxed = true)
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

        val event = mockk<MotionEvent>(relaxed = true)
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
        val event = mockk<MotionEvent>(relaxed = true)
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
        val event = mockk<KeyEvent>(relaxed = true)
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

    @Test
    fun joystickLeftStick_movesCursorAndSendsTouchMove() {
        val initialX = inputEngine.cursorX
        val initialY = inputEngine.cursorY

        val event = mockk<MotionEvent>(relaxed = true)
        every { event.source } returns InputDevice.SOURCE_JOYSTICK
        every { event.getAxisValue(MotionEvent.AXIS_X) } returns 0.5f
        every { event.getAxisValue(MotionEvent.AXIS_Y) } returns -0.5f
        every { event.getAxisValue(MotionEvent.AXIS_Z) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_RZ) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_HAT_X) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_HAT_Y) } returns 0f

        val handled = inputEngine.onGenericMotionEvent(event, viewWidth = 1920, viewHeight = 1080)
        assertThat(handled).isTrue()

        assertThat(inputEngine.cursorX).isGreaterThan(initialX)
        assertThat(inputEngine.cursorY).isLessThan(initialY)

        val slot = slot<ControlMessage.Touch>()
        verify { mockWriter.send(capture(slot)) }
        assertThat(slot.captured.action).isEqualTo(ControlMessage.ACTION_MOVE)
    }

    @Test
    fun joystickRightStick_sendsScrollMessage() {
        val event = mockk<MotionEvent>(relaxed = true)
        every { event.source } returns InputDevice.SOURCE_JOYSTICK
        every { event.getAxisValue(MotionEvent.AXIS_X) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_Y) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_Z) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_RZ) } returns 0.8f
        every { event.getAxisValue(MotionEvent.AXIS_HAT_X) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_HAT_Y) } returns 0f

        val handled = inputEngine.onGenericMotionEvent(event, viewWidth = 1920, viewHeight = 1080)
        assertThat(handled).isTrue()

        val slot = slot<ControlMessage.Scroll>()
        verify { mockWriter.send(capture(slot)) }
        assertThat(slot.captured.vScroll).isEqualTo(-0.8f)
    }

    @Test
    fun joystickDpadHat_sendsDpadKeycode() {
        val event = mockk<MotionEvent>(relaxed = true)
        every { event.source } returns InputDevice.SOURCE_JOYSTICK
        every { event.getAxisValue(MotionEvent.AXIS_X) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_Y) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_Z) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_RZ) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_HAT_X) } returns 1.0f
        every { event.getAxisValue(MotionEvent.AXIS_HAT_Y) } returns 0f

        val handled = inputEngine.onGenericMotionEvent(event, viewWidth = 1920, viewHeight = 1080)
        assertThat(handled).isTrue()

        verify {
            mockWriter.send(ControlMessage.Keycode(ControlMessage.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            mockWriter.send(ControlMessage.Keycode(ControlMessage.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        }
    }

    @Test
    fun gamepadButtonA_inTrackpadMode_sendsTouchClickAtCursor() {
        inputEngine.inputMode = InputMode.TRACKPAD

        val event = mockk<KeyEvent>(relaxed = true)
        every { event.action } returns KeyEvent.ACTION_DOWN
        every { event.keyCode } returns KeyEvent.KEYCODE_BUTTON_A

        val handled = inputEngine.onKeyEvent(event)
        assertThat(handled).isTrue()

        val slot = slot<ControlMessage.Touch>()
        verify { mockWriter.send(capture(slot)) }

        val touch = slot.captured
        assertThat(touch.action).isEqualTo(ControlMessage.ACTION_DOWN)
        assertThat(touch.buttons).isEqualTo(ControlMessage.BUTTON_PRIMARY)
        assertThat(touch.x).isEqualTo(inputEngine.cursorX.toInt())
        assertThat(touch.y).isEqualTo(inputEngine.cursorY.toInt())
    }

    @Test
    fun trackpadDoubleTapAndHold_triggersDragWithPrimaryButton() {
        var now = 1000L
        val trackpadEngine = InputEngine(
            writer = mockWriter,
            displayWidth = 1920,
            displayHeight = 1080,
            inputMode = InputMode.TRACKPAD,
            timeProvider = { now }
        )

        fun fingerEvent(action: Int, x: Float, y: Float): MotionEvent = mockk<MotionEvent>(relaxed = true).also {
            every { it.getToolType(0) } returns MotionEvent.TOOL_TYPE_FINGER
            every { it.pointerCount } returns 1
            every { it.actionMasked } returns action
            every { it.x } returns x
            every { it.y } returns y
        }

        // Quick tap (DOWN -> UP)
        trackpadEngine.onTouchEvent(fingerEvent(MotionEvent.ACTION_DOWN, 100f, 100f), 1920, 1080)
        now += 50L
        trackpadEngine.onTouchEvent(fingerEvent(MotionEvent.ACTION_UP, 100f, 100f), 1920, 1080)

        // Second tap within 300ms -> enters drag mode and moves while holding
        now += 100L
        trackpadEngine.onTouchEvent(fingerEvent(MotionEvent.ACTION_DOWN, 100f, 100f), 1920, 1080)
        now += 100L
        trackpadEngine.onTouchEvent(fingerEvent(MotionEvent.ACTION_MOVE, 140f, 120f), 1920, 1080)
        now += 100L
        trackpadEngine.onTouchEvent(fingerEvent(MotionEvent.ACTION_UP, 140f, 120f), 1920, 1080)

        val messages = mutableListOf<ControlMessage>()
        verify(exactly = 5) { mockWriter.send(capture(messages)) }

        val dragDown = messages[2] as ControlMessage.Touch
        val dragMove = messages[3] as ControlMessage.Touch
        val dragUp = messages[4] as ControlMessage.Touch

        assertThat(dragDown.action).isEqualTo(ControlMessage.ACTION_DOWN)
        assertThat(dragDown.actionButton).isEqualTo(ControlMessage.BUTTON_PRIMARY)
        assertThat(dragDown.buttons).isEqualTo(ControlMessage.BUTTON_PRIMARY)

        assertThat(dragMove.action).isEqualTo(ControlMessage.ACTION_MOVE)
        assertThat(dragMove.buttons).isEqualTo(ControlMessage.BUTTON_PRIMARY)
        assertThat(dragMove.x).isGreaterThan(dragDown.x)

        assertThat(dragUp.action).isEqualTo(ControlMessage.ACTION_UP)
        assertThat(dragUp.actionButton).isEqualTo(ControlMessage.BUTTON_PRIMARY)
        assertThat(dragUp.buttons).isEqualTo(0)
    }

    @Test
    fun trackpadTwoFingerSwipe_emitsHorizontalAndVerticalScroll() {
        var now = 1000L
        val trackpadEngine = InputEngine(
            writer = mockWriter,
            displayWidth = 1920,
            displayHeight = 1080,
            inputMode = InputMode.TRACKPAD,
            timeProvider = { now }
        )

        val down = mockk<MotionEvent>(relaxed = true).also {
            every { it.getToolType(0) } returns MotionEvent.TOOL_TYPE_FINGER
            every { it.pointerCount } returns 1
            every { it.actionMasked } returns MotionEvent.ACTION_DOWN
            every { it.x } returns 200f
            every { it.y } returns 300f
        }
        val pointerDown = mockk<MotionEvent>(relaxed = true).also {
            every { it.getToolType(0) } returns MotionEvent.TOOL_TYPE_FINGER
            every { it.pointerCount } returns 2
            every { it.actionMasked } returns MotionEvent.ACTION_POINTER_DOWN
            every { it.getX(0) } returns 200f
            every { it.getY(0) } returns 300f
        }
        val swipe = mockk<MotionEvent>(relaxed = true).also {
            every { it.getToolType(0) } returns MotionEvent.TOOL_TYPE_FINGER
            every { it.pointerCount } returns 2
            every { it.actionMasked } returns MotionEvent.ACTION_MOVE
            every { it.getX(0) } returns 225f
            every { it.getY(0) } returns 285f
        }
        val finalUp = mockk<MotionEvent>(relaxed = true).also {
            every { it.getToolType(0) } returns MotionEvent.TOOL_TYPE_FINGER
            every { it.pointerCount } returns 1
            every { it.actionMasked } returns MotionEvent.ACTION_UP
        }

        trackpadEngine.onTouchEvent(down, 1920, 1080)
        now += 10L
        trackpadEngine.onTouchEvent(pointerDown, 1920, 1080)
        now += 10L
        trackpadEngine.onTouchEvent(swipe, 1920, 1080)
        now += 10L
        trackpadEngine.onTouchEvent(finalUp, 1920, 1080)

        val scrollSlot = slot<ControlMessage.Scroll>()
        verify(exactly = 1) { mockWriter.send(capture(scrollSlot)) }
        assertThat(scrollSlot.captured.hScroll).isWithin(0.01f).of(2.5f)
        assertThat(scrollSlot.captured.vScroll).isWithin(0.01f).of(-1.5f)
    }

    @Test
    fun hardwareMouse_buttonDownAndUp_populatesActionButton() {
        val downEvent = mockk<MotionEvent>(relaxed = true).also {
            every { it.getToolType(0) } returns MotionEvent.TOOL_TYPE_MOUSE
            every { it.actionMasked } returns MotionEvent.ACTION_DOWN
            every { it.actionButton } returns MotionEvent.BUTTON_SECONDARY
            every { it.buttonState } returns MotionEvent.BUTTON_SECONDARY
            every { it.x } returns 960f
            every { it.y } returns 540f
        }
        val upEvent = mockk<MotionEvent>(relaxed = true).also {
            every { it.getToolType(0) } returns MotionEvent.TOOL_TYPE_MOUSE
            every { it.actionMasked } returns MotionEvent.ACTION_UP
            every { it.actionButton } returns MotionEvent.BUTTON_SECONDARY
            every { it.buttonState } returns 0
            every { it.x } returns 960f
            every { it.y } returns 540f
        }

        inputEngine.onTouchEvent(downEvent, 1920, 1080)
        inputEngine.onTouchEvent(upEvent, 1920, 1080)

        val messages = mutableListOf<ControlMessage.Touch>()
        verify(exactly = 2) { mockWriter.send(capture(messages)) }
        assertThat(messages[0].actionButton).isEqualTo(ControlMessage.BUTTON_SECONDARY)
        assertThat(messages[0].buttons).isEqualTo(ControlMessage.BUTTON_SECONDARY)
        assertThat(messages[1].actionButton).isEqualTo(ControlMessage.BUTTON_SECONDARY)
        assertThat(messages[1].buttons).isEqualTo(0)
    }
}
