package org.localdex.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.localdex.core.scrcpy.input.InputMode
import kotlin.math.roundToInt

@Composable
fun SessionPillToolbar(
    inputMode: InputMode,
    isPointerCaptured: Boolean = false,
    onToggleInputMode: () -> Unit,
    onTogglePointerCapture: (() -> Unit)? = null,
    onTriggerKeyboard: () -> Unit,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(40f) }
    var offsetY by remember { mutableFloatStateOf(80f) }
    var isExpanded by remember { mutableStateOf(false) }
    var isTouching by remember { mutableStateOf(false) }

    // Auto-dim pill to 0.35 alpha after 3 seconds of inactivity
    var alphaVal by remember { mutableFloatStateOf(0.95f) }
    LaunchedEffect(isTouching, isExpanded) {
        if (!isTouching && !isExpanded) {
            delay(3000)
            alphaVal = 0.35f
        } else {
            alphaVal = 0.95f
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Floating Draggable Pill
        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .alpha(alphaVal)
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isTouching = true
                            alphaVal = 0.95f
                        },
                        onDragEnd = { isTouching = false },
                        onDragCancel = { isTouching = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    )
                },
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        isExpanded = !isExpanded
                        alphaVal = 0.95f
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.Close else Icons.Default.DragHandle,
                        contentDescription = "Session Menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        // Toggle Input Mode (Trackpad vs Direct Touch)
                        IconButton(
                            onClick = onToggleInputMode,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (inputMode == InputMode.TRACKPAD) Icons.Default.Mouse else Icons.Default.TouchApp,
                                contentDescription = "Toggle Input Mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Toggle Pointer Capture / Mouse Lock
                        if (onTogglePointerCapture != null) {
                            IconButton(
                                onClick = onTogglePointerCapture,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.PanTool,
                                    contentDescription = if (isPointerCaptured) "Release Mouse Pointer" else "Lock Mouse Pointer",
                                    tint = if (isPointerCaptured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Trigger Soft Keyboard
                        IconButton(
                            onClick = onTriggerKeyboard,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Keyboard,
                                contentDescription = "Show Keyboard",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Stop DeX Session
                        IconButton(
                            onClick = onStopSession,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Stop DeX",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
