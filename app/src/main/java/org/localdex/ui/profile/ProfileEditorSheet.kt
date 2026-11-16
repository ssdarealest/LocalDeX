package org.localdex.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.localdex.core.scrcpy.model.DisplayProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorSheet(
    sheetState: SheetState,
    initialProfile: DisplayProfile,
    onSaveProfile: (DisplayProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var width by remember { mutableIntStateOf(initialProfile.width) }
    var height by remember { mutableIntStateOf(initialProfile.height) }
    var dpi by remember { mutableFloatStateOf(initialProfile.dpi.toFloat()) }
    var maxFps by remember { mutableIntStateOf(initialProfile.maxFps) }
    var bitRateMbps by remember { mutableIntStateOf(initialProfile.bitRateMbps) }
    var codec by remember { mutableStateOf(initialProfile.codec) }

    val logicalWidthDp = (width * 160f / dpi).toInt()
    val logicalHeightDp = (height * 160f / dpi).toInt()
    val currentAspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 16f / 9f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Display Profile Editor",
                style = MaterialTheme.typography.headlineMedium
            )

            // Live Aspect Ratio Wireframe Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Effective Logical Viewport",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$logicalWidthDp × $logicalHeightDp dp",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Visual Aspect Wireframe Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(currentAspectRatio.coerceIn(1.0f, 2.5f))
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${width}x${height} (${String.format("%.2f", currentAspectRatio)}:1)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Resolution inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = width.toString(),
                    onValueChange = { width = it.toIntOrNull() ?: width },
                    label = { Text("Width (px)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = height.toString(),
                    onValueChange = { height = it.toIntOrNull() ?: height },
                    label = { Text("Height (px)") },
                    modifier = Modifier.weight(1f)
                )
            }

            // DPI Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Display Density (DPI):", style = MaterialTheme.typography.bodyMedium)
                    Text("${dpi.toInt()} DPI", style = MaterialTheme.typography.titleMedium)
                }
                Slider(
                    value = dpi,
                    onValueChange = { dpi = it },
                    valueRange = 120f..480f,
                    steps = 11
                )
            }

            // Frame Rate Selection
            Column {
                Text("Refresh Rate:", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = maxFps == 60,
                        onClick = { maxFps = 60 },
                        label = { Text("60 FPS") }
                    )
                    FilterChip(
                        selected = maxFps == 120,
                        onClick = { maxFps = 120 },
                        label = { Text("120 FPS") }
                    )
                }
            }

            // Codec Selection
            Column {
                Text("Video Codec:", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = codec == DisplayProfile.VideoCodec.H264,
                        onClick = { codec = DisplayProfile.VideoCodec.H264 },
                        label = { Text("H.264 (Low Latency)") }
                    )
                    FilterChip(
                        selected = codec == DisplayProfile.VideoCodec.H265,
                        onClick = { codec = DisplayProfile.VideoCodec.H265 },
                        label = { Text("H.265 (High Efficiency)") }
                    )
                }
            }

            // Bitrate Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bitrate:", style = MaterialTheme.typography.bodyMedium)
                    Text("$bitRateMbps Mbps", style = MaterialTheme.typography.titleMedium)
                }
                Slider(
                    value = bitRateMbps.toFloat(),
                    onValueChange = { bitRateMbps = it.toInt() },
                    valueRange = 8f..32f,
                    steps = 5
                )
            }

            // Save Action
            Button(
                onClick = {
                    val updated = DisplayProfile(
                        id = "custom_${System.currentTimeMillis()}",
                        name = "Custom (${width}x${height})",
                        width = width,
                        height = height,
                        dpi = dpi.toInt(),
                        maxFps = maxFps,
                        bitRateMbps = bitRateMbps,
                        codec = codec,
                        isCustom = true
                    )
                    onSaveProfile(updated)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save and Apply Profile")
            }
        }
    }
}
