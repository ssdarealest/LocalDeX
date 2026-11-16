package org.localdex.ui.setup

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.localdex.ui.theme.StatusSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardSheet(
    sheetState: SheetState,
    hasNotificationPermission: Boolean,
    isDeveloperOptionsEnabled: Boolean,
    isWirelessDebuggingEnabled: Boolean,
    hasSecureSettingsPermission: Boolean,
    discoveredPairingPort: Int?,
    discoveredConnectPort: Int?,
    onRequestNotificationPermission: () -> Unit,
    onTriggerPairingNotification: (pairingPort: Int, connectPort: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Guided Setup Wizard",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Complete these 4 one-time steps to configure loopback Samsung DeX.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Step 1: Notifications
            StepCard(
                stepNumber = "1",
                title = "Notification Permission",
                description = "Required to display the 6-digit pairing code entry box while in Android Settings.",
                icon = Icons.Default.Notifications,
                isCompleted = hasNotificationPermission,
                actionLabel = "Grant Permission",
                onAction = onRequestNotificationPermission
            )

            // Step 2: Developer Options
            StepCard(
                stepNumber = "2",
                title = "Developer Options",
                description = "Enable Developer Options by tapping 'Build number' 7 times in About Phone.",
                icon = Icons.Default.DeveloperMode,
                isCompleted = isDeveloperOptionsEnabled,
                actionLabel = "Open Settings",
                onAction = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    context.startActivity(intent)
                }
            )

            // Step 3: Wireless Debugging & Pairing
            StepCard(
                stepNumber = "3",
                title = "Wireless Debugging Pairing",
                description = if (discoveredPairingPort != null && discoveredPairingPort > 0) {
                    "Pairing service detected on port $discoveredPairingPort! Tap below to open notification reply."
                } else {
                    "Toggle Wireless Debugging ON and tap 'Pair device with pairing code'."
                },
                icon = Icons.Default.WifiTethering,
                isCompleted = hasSecureSettingsPermission,
                actionLabel = if (discoveredPairingPort != null && discoveredPairingPort > 0) "Show Reply Notification" else "Open Wireless Debugging",
                onAction = {
                    if (discoveredPairingPort != null && discoveredPairingPort > 0) {
                        onTriggerPairingNotification(discoveredPairingPort, discoveredConnectPort ?: 0)
                    } else {
                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        context.startActivity(intent)
                    }
                }
            )

            // Step 4: Auto-Reconnect Permission
            StepCard(
                stepNumber = "4",
                title = "Auto-Reconnect Permission",
                description = "WRITE_SECURE_SETTINGS lets LocalDeX automatically keep Wireless Debugging alive across reboots.",
                icon = Icons.Default.Security,
                isCompleted = hasSecureSettingsPermission,
                actionLabel = "Granted via Loopback",
                onAction = null
            )
        }
    }
}

@Composable
fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isCompleted: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isCompleted) StatusSuccess else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Step $stepNumber: $title",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (isCompleted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = StatusSuccess
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isCompleted && actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
