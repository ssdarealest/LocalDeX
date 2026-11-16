package org.localdex.core.scrcpy.input

import android.view.KeyEvent
import android.view.MotionEvent
import org.localdex.core.scrcpy.control.ControlMessage
import org.localdex.core.scrcpy.control.ControlSocketWriter
import kotlin.math.roundToInt

enum class InputMode {
    TRACKPAD,
    DIRECT_TOUCH
}

/**
 * Translates Android MotionEvents and KeyEvents into scrcpy control protocol packets.
 * Supports Direct Touch, Virtual Trackpad, S-Pen / Stylus (with pressure & button),
 * and Hardware Mouse (absolute & pointer-captured relative movement).
 */
class InputEngine(
    private val writer: ControlSocketWriter,
    var displayWidth: Int,
    var displayHeight: Int,
    var inputMode: InputMode = InputMode.TRACKPAD
) {
    // Current virtual cursor coordinates (for Trackpad & Relative Mouse mode)
    var cursorX = displayWidth / 2f
        private set
    var cursorY = displayHeight / 2f
        private set

    // Trackpad gesture tracking
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var touchDownTime = 0L

    companion object {
        private const val TAP_THRESHOLD_MS = 200L
        private const val TRACKPAD_SENSITIVITY = 1.35f
    }

    fun updateDisplayDimensions(width: Int, height: Int) {
        this.displayWidth = width
        this.displayHeight = height
        cursorX = cursorX.coerceIn(0f, width.toFloat())
        cursorY = cursorY.coerceIn(0f, height.toFloat())
    }

    /**
     * Handles touch events from the Viewer SurfaceView.
     */
    fun onTouchEvent(
        event: MotionEvent,
        viewWidth: Int,
        viewHeight: Int,
        isPointerCaptured: Boolean = false
    ): Boolean {
        val toolType = event.getToolType(0)

        // S-Pen / Stylus has dedicated direct precision routing with pressure
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
            return handleStylusEvent(event, viewWidth, viewHeight)
        }

        // Hardware mouse routes through mouse handler (with pointer capture support)
        if (toolType == MotionEvent.TOOL_TYPE_MOUSE) {
            return handleHardwareMouseEvent(event, viewWidth, viewHeight, isPointerCaptured)
        }

        return when (inputMode) {
            InputMode.TRACKPAD -> handleTrackpadTouch(event)
            InputMode.DIRECT_TOUCH -> handleDirectTouch(event, viewWidth, viewHeight)
        }
    }

    /**
     * Handles generic motion events (mouse hover, mouse wheel scroll, stylus hover).
     */
    fun onGenericMotionEvent(
        event: MotionEvent,
        viewWidth: Int,
        viewHeight: Int,
        isPointerCaptured: Boolean = false
    ): Boolean {
        val toolType = event.getToolType(0)
        return when (toolType) {
            MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> {
                handleStylusEvent(event, viewWidth, viewHeight)
            }
            MotionEvent.TOOL_TYPE_MOUSE -> {
                handleHardwareMouseEvent(event, viewWidth, viewHeight, isPointerCaptured)
            }
            else -> false
        }
    }

    private fun handleDirectTouch(event: MotionEvent, viewWidth: Int, viewHeight: Int): Boolean {
        if (viewWidth <= 0 || viewHeight <= 0) return false

        val scaleX = displayWidth.toFloat() / viewWidth.toFloat()
        val scaleY = displayHeight.toFloat() / viewHeight.toFloat()

        val pointerCount = event.pointerCount
        for (i in 0 until pointerCount) {
            val pointerId = event.getPointerId(i).toLong()
            val x = (event.getX(i) * scaleX).roundToInt().coerceIn(0, displayWidth)
            val y = (event.getY(i) * scaleY).roundToInt().coerceIn(0, displayHeight)
            val pressure = event.getPressure(i)

            val action = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> ControlMessage.ACTION_DOWN
                MotionEvent.ACTION_MOVE -> ControlMessage.ACTION_MOVE
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> ControlMessage.ACTION_UP
                else -> return false
            }

            writer.send(
                ControlMessage.Touch(
                    action = action,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    displayWidth = displayWidth,
                    displayHeight = displayHeight,
                    pressure = pressure
                )
            )
        }
        return true
    }

    private fun handleStylusEvent(event: MotionEvent, viewWidth: Int, viewHeight: Int): Boolean {
        if (viewWidth <= 0 || viewHeight <= 0) return false

        val scaleX = displayWidth.toFloat() / viewWidth.toFloat()
        val scaleY = displayHeight.toFloat() / viewHeight.toFloat()
        val x = (event.x * scaleX).roundToInt().coerceIn(0, displayWidth)
        val y = (event.y * scaleY).roundToInt().coerceIn(0, displayHeight)
        val pressure = event.getPressure(0)

        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> ControlMessage.ACTION_DOWN
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> ControlMessage.ACTION_MOVE
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ControlMessage.ACTION_UP
            else -> return false
        }

        val isEraser = event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
        val isButtonPressed = (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0

        val buttons = when {
            isEraser || isButtonPressed -> ControlMessage.BUTTON_SECONDARY
            action != ControlMessage.ACTION_UP && event.actionMasked != MotionEvent.ACTION_HOVER_MOVE -> ControlMessage.BUTTON_PRIMARY
            else -> 0
        }

        writer.send(
            ControlMessage.Touch(
                action = action,
                pointerId = 0,
                x = x,
                y = y,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                pressure = pressure,
                buttons = buttons
            )
        )
        return true
    }

    private fun handleTrackpadTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTime = System.currentTimeMillis()
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerCount = event.pointerCount
                if (pointerCount == 1) {
                    val dx = (event.x - lastTouchX) * TRACKPAD_SENSITIVITY
                    val dy = (event.y - lastTouchY) * TRACKPAD_SENSITIVITY
                    cursorX = (cursorX + dx).coerceIn(0f, displayWidth.toFloat())
                    cursorY = (cursorY + dy).coerceIn(0f, displayHeight.toFloat())

                    lastTouchX = event.x
                    lastTouchY = event.y

                    if (isDragging) {
                        writer.send(
                            ControlMessage.Touch(
                                action = ControlMessage.ACTION_MOVE,
                                pointerId = 0,
                                x = cursorX.roundToInt(),
                                y = cursorY.roundToInt(),
                                displayWidth = displayWidth,
                                displayHeight = displayHeight,
                                buttons = ControlMessage.BUTTON_PRIMARY
                            )
                        )
                    }
                } else if (pointerCount == 2) {
                    // Two-finger scroll
                    val dy = (event.getY(0) - lastTouchY) / 10f
                    writer.send(
                        ControlMessage.Scroll(
                            x = cursorX.roundToInt(),
                            y = cursorY.roundToInt(),
                            displayWidth = displayWidth,
                            displayHeight = displayHeight,
                            hScroll = 0f,
                            vScroll = dy
                        )
                    )
                    lastTouchY = event.getY(0)
                }
            }

            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - touchDownTime
                if (duration < TAP_THRESHOLD_MS) {
                    // Tap = Left click
                    val x = cursorX.roundToInt()
                    val y = cursorY.roundToInt()
                    writer.send(
                        ControlMessage.Touch(
                            action = ControlMessage.ACTION_DOWN,
                            pointerId = 0,
                            x = x,
                            y = y,
                            displayWidth = displayWidth,
                            displayHeight = displayHeight,
                            buttons = ControlMessage.BUTTON_PRIMARY
                        )
                    )
                    writer.send(
                        ControlMessage.Touch(
                            action = ControlMessage.ACTION_UP,
                            pointerId = 0,
                            x = x,
                            y = y,
                            displayWidth = displayWidth,
                            displayHeight = displayHeight
                        )
                    )
                } else if (isDragging) {
                    isDragging = false
                    writer.send(
                        ControlMessage.Touch(
                            action = ControlMessage.ACTION_UP,
                            pointerId = 0,
                            x = cursorX.roundToInt(),
                            y = cursorY.roundToInt(),
                            displayWidth = displayWidth,
                            displayHeight = displayHeight
                        )
                    )
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Two finger tap = Right click
                if (event.pointerCount == 2 && System.currentTimeMillis() - touchDownTime < TAP_THRESHOLD_MS) {
                    val x = cursorX.roundToInt()
                    val y = cursorY.roundToInt()
                    writer.send(
                        ControlMessage.Touch(
                            action = ControlMessage.ACTION_DOWN,
                            pointerId = 0,
                            x = x,
                            y = y,
                            displayWidth = displayWidth,
                            displayHeight = displayHeight,
                            buttons = ControlMessage.BUTTON_SECONDARY
                        )
                    )
                    writer.send(
                        ControlMessage.Touch(
                            action = ControlMessage.ACTION_UP,
                            pointerId = 0,
                            x = x,
                            y = y,
                            displayWidth = displayWidth,
                            displayHeight = displayHeight
                        )
                    )
                }
            }
        }
        return true
    }

    fun handleHardwareMouseEvent(
        event: MotionEvent,
        viewWidth: Int,
        viewHeight: Int,
        isPointerCaptured: Boolean = false
    ): Boolean {
        val x: Int
        val y: Int

        if (isPointerCaptured) {
            val relX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
            val relY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
            cursorX = (cursorX + relX).coerceIn(0f, displayWidth.toFloat())
            cursorY = (cursorY + relY).coerceIn(0f, displayHeight.toFloat())
            x = cursorX.roundToInt()
            y = cursorY.roundToInt()
        } else {
            if (viewWidth <= 0 || viewHeight <= 0) return false
            val scaleX = displayWidth.toFloat() / viewWidth.toFloat()
            val scaleY = displayHeight.toFloat() / viewHeight.toFloat()
            x = (event.x * scaleX).roundToInt().coerceIn(0, displayWidth)
            y = (event.y * scaleY).roundToInt().coerceIn(0, displayHeight)
            cursorX = x.toFloat()
            cursorY = y.toFloat()
        }

        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> ControlMessage.ACTION_DOWN
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> ControlMessage.ACTION_MOVE
            MotionEvent.ACTION_UP -> ControlMessage.ACTION_UP
            MotionEvent.ACTION_SCROLL -> {
                val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                writer.send(
                    ControlMessage.Scroll(
                        x = x,
                        y = y,
                        displayWidth = displayWidth,
                        displayHeight = displayHeight,
                        hScroll = hScroll,
                        vScroll = vScroll
                    )
                )
                return true
            }
            else -> return false
        }

        val buttons = when {
            (event.buttonState and MotionEvent.BUTTON_PRIMARY) != 0 -> ControlMessage.BUTTON_PRIMARY
            (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0 -> ControlMessage.BUTTON_SECONDARY
            (event.buttonState and MotionEvent.BUTTON_TERTIARY) != 0 -> ControlMessage.BUTTON_TERTIARY
            else -> 0
        }

        writer.send(
            ControlMessage.Touch(
                action = action,
                pointerId = 0,
                x = x,
                y = y,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                buttons = buttons
            )
        )
        return true
    }

    /**
     * Forwards physical keyboard events.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> ControlMessage.ACTION_DOWN
            KeyEvent.ACTION_UP -> ControlMessage.ACTION_UP
            else -> return false
        }

        writer.send(
            ControlMessage.Keycode(
                action = action,
                keycode = event.keyCode,
                repeat = event.repeatCount,
                metaState = event.metaState
            )
        )
        return true
    }

    /**
     * Injects a Back key event directly to Samsung DeX.
     */
    fun sendBackGesture() {
        writer.send(ControlMessage.BackOrScreenOn(ControlMessage.ACTION_DOWN))
        writer.send(ControlMessage.BackOrScreenOn(ControlMessage.ACTION_UP))
    }
}
