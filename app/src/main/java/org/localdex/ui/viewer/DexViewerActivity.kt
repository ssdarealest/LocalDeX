package org.localdex.ui.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.localdex.core.scrcpy.control.ControlSocketWriter
import org.localdex.core.scrcpy.decoder.VideoStreamDecoder
import org.localdex.core.scrcpy.input.InputEngine
import org.localdex.core.scrcpy.input.InputMode
import org.localdex.core.scrcpy.server.ScrcpyServerManager
import org.localdex.service.DexSessionService
import org.localdex.service.SessionState
import org.localdex.ui.foldable.DevicePosture
import org.localdex.ui.foldable.FoldablePostureTracker
import org.localdex.ui.foldable.FoldableState
import org.localdex.ui.theme.LocalDeXTheme

class DexViewerActivity : ComponentActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private var videoDecoder: VideoStreamDecoder? = null
    private var controlWriter: ControlSocketWriter? = null
    private var inputEngine: InputEngine? = null

    private val inputModeState = MutableStateFlow(InputMode.TRACKPAD)
    private val isPointerCapturedState = MutableStateFlow(false)
    private val imeHeightState = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during DeX workstation use
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        // Root layout holding SurfaceView and Compose Overlay
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Hardware-accelerated SurfaceView for zero-copy MediaCodec rendering
        surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            holder.addCallback(this@DexViewerActivity)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        rootLayout.addView(surfaceView)

        // 2. Compose overlay for draggable floating session pill
        val composeOverlay = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setContent {
                val mode by inputModeState.collectAsState()
                val isCaptured by isPointerCapturedState.collectAsState()
                val imeHeight by imeHeightState.collectAsState()
                LocalDeXTheme {
                    SessionPillToolbar(
                        inputMode = mode,
                        isPointerCaptured = isCaptured,
                        imeBottomInset = imeHeight,
                        onToggleInputMode = {
                            val nextMode = if (mode == InputMode.TRACKPAD) InputMode.DIRECT_TOUCH else InputMode.TRACKPAD
                            inputModeState.value = nextMode
                            inputEngine?.inputMode = nextMode
                        },
                        onTogglePointerCapture = {
                            if (surfaceView.hasPointerCapture()) {
                                surfaceView.releasePointerCapture()
                            } else {
                                surfaceView.requestPointerCapture()
                            }
                        },
                        onTriggerKeyboard = {
                            toggleSoftKeyboard()
                        },
                        onStopSession = {
                            stopSessionAndExit()
                        }
                    )
                }
            }
        }
        rootLayout.addView(composeOverlay)

        // Apply window insets listener to track soft keyboard height
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            imeHeightState.value = imeBottom
            insets
        }

        setContentView(rootLayout)

        // 3. System Back Navigation Forwarding: route Back key into DeX or release pointer lock
        onBackPressedDispatcher.addCallback(this) {
            if (surfaceView.hasPointerCapture()) {
                surfaceView.releasePointerCapture()
            } else {
                inputEngine?.sendBackGesture()
            }
        }

        // 4. Touch & Generic Motion Dispatch Interceptor (Supports S-Pen stylus, mouse, and gestures)
        surfaceView.setOnTouchListener { _, event ->
            inputEngine?.onTouchEvent(
                event = event,
                viewWidth = surfaceView.width,
                viewHeight = surfaceView.height,
                isPointerCaptured = surfaceView.hasPointerCapture()
            ) ?: false
        }

        surfaceView.setOnGenericMotionListener { _, event ->
            inputEngine?.onGenericMotionEvent(
                event = event,
                viewWidth = surfaceView.width,
                viewHeight = surfaceView.height,
                isPointerCaptured = surfaceView.hasPointerCapture()
            ) ?: false
        }

        surfaceView.setOnCapturedPointerListener { _, event ->
            inputEngine?.handleHardwareMouseEvent(
                event = event,
                viewWidth = surfaceView.width,
                viewHeight = surfaceView.height,
                isPointerCaptured = true
            ) ?: false
        }

        // 5. Foldable Hinge & Posture Awareness: adjust layout for tabletop flex mode
        val postureTracker = FoldablePostureTracker(this)
        lifecycleScope.launch {
            postureTracker.postureFlow.collect { foldableState ->
                adjustLayoutForPosture(foldableState)
            }
        }
    }

    private fun adjustLayoutForPosture(state: FoldableState) {
        val hinge = state.hingeBounds
        val lp = surfaceView.layoutParams as? FrameLayout.LayoutParams ?: return
        if (state.posture == DevicePosture.TABLETOP && hinge != null && hinge.top > 0) {
            lp.height = hinge.top
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT
            surfaceView.layoutParams = lp
        } else {
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT
            surfaceView.layoutParams = lp
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val currentState = DexSessionService.sessionState.value
        val profile = DexSessionService.activeProfile
        val port = ScrcpyServerManager.DEFAULT_SCRCPY_PORT

        lifecycleScope.launch(Dispatchers.IO) {
            // Connect control socket
            val writer = ControlSocketWriter(port = port)
            if (writer.connect()) {
                controlWriter = writer
                inputEngine = InputEngine(
                    writer = writer,
                    displayWidth = profile.width,
                    displayHeight = profile.height,
                    inputMode = inputModeState.value
                )
            }

            // Connect video decoder
            val decoder = VideoStreamDecoder(
                port = port,
                surface = holder.surface
            )
            if (decoder.start()) {
                videoDecoder = decoder
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Handle orientation or surface dimension changes
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        videoDecoder?.close()
        videoDecoder = null
        controlWriter?.close()
        controlWriter = null
        inputEngine = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (inputEngine?.onKeyEvent(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun toggleSoftKeyboard() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val isImeVisible = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (isImeVisible) {
            windowInsetsController.hide(WindowInsetsCompat.Type.ime())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun stopSessionAndExit() {
        val stopIntent = Intent(this, DexSessionService::class.java).apply {
            action = DexSessionService.ACTION_STOP_SESSION
        }
        startService(stopIntent)
        finish()
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        super.onPointerCaptureChanged(hasCapture)
        isPointerCapturedState.value = hasCapture
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }
}
