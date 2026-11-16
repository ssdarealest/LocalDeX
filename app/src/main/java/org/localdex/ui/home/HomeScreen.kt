package org.localdex.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.localdex.core.scrcpy.model.DisplayProfile
import org.localdex.service.SessionState
import org.localdex.ui.theme.StatusActive
import org.localdex.ui.theme.StatusError
import org.localdex.ui.theme.StatusSuccess
import org.localdex.ui.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sessionState: SessionState,
    isPaired: Boolean,
    hasSecureSettings: Boolean,
    activeProfile: DisplayProfile,
    allProfiles: List<DisplayProfile>,
    onSelectProfile: (DisplayProfile) -> Unit,
    onStartDeX: () -> Unit,
    onStopDeX: () -> Unit,
    onResumeDeX: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenProfileEditor: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LocalDeX", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = onOpenProfileEditor) {
                        Icon(Icons.Default.Tune, contentDescription = "Edit Profiles")
                    }
                    IconButton(onClick = onOpenSetup) {
                        Icon(Icons.Default.Settings, contentDescription = "Setup Wizard")
                    }
                }
            )
        },
        floatingActionButton = {
            when (sessionState) {
                is SessionState.Running -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExtendedFloatingActionButton(
                            onClick = onStopDeX,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            icon = { Icon(Icons.Default.Stop, contentDescription = null) },
                            text = { Text("Stop DeX") }
                        )
                        ExtendedFloatingActionButton(
                            onClick = onResumeDeX,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            text = { Text("Resume Viewer") }
                        )
                    }
                }
                is SessionState.Starting -> {
                    ExtendedFloatingActionButton(
                        onClick = {},
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        icon = { Icon(Icons.Default.DesktopWindows, contentDescription = null) },
                        text = { Text("Starting...") }
                    )
                }
                else -> {
                    ExtendedFloatingActionButton(
                        onClick = onStartDeX,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        text = { Text("Start DeX") }
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Readiness Card
            item {
                HeroStatusCard(
                    sessionState = sessionState,
                    isPaired = isPaired,
                    hasSecureSettings = hasSecureSettings,
                    onOpenSetup = onOpenSetup
                )
            }

            // 2. Active Display Profile Quick Card
            item {
                ActiveProfileSummaryCard(
                    profile = activeProfile,
                    onOpenEditor = onOpenProfileEditor
                )
            }

            // 3. Quick Profile Selector Chips
            item {
                Text(
                    text = "Display Presets",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(allProfiles) { profile ->
                ProfileSelectionItem(
                    profile = profile,
                    isSelected = profile.id == activeProfile.id,
                    onSelect = { onSelectProfile(profile) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
            }
        }
    }
}

@Composable
fun HeroStatusCard(
    sessionState: SessionState,
    isPaired: Boolean,
    hasSecureSettings: Boolean,
    onOpenSetup: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            val (statusText, statusColor, subtitle) = when {
                sessionState is SessionState.Running -> Triple(
                    "Session Active (Display #${sessionState.sessionInfo.displayId})",
                    StatusActive,
                    "Samsung DeX is running on virtual display"
                )
                sessionState is SessionState.Starting -> Triple(
                    "Launching Virtual Display...",
                    StatusWarning,
                    "Starting scrcpy-server daemon on loopback"
                )
                !isPaired -> Triple(
                    "Pairing Required",
                    StatusWarning,
                    "Wireless Debugging pairing code needed"
                )
                !hasSecureSettings -> Triple(
                    "Ready to Connect",
                    StatusWarning,
                    "Auto-reconnect permission not yet held"
                )
                else -> Triple(
                    "Ready to Launch",
                    StatusSuccess,
                    "Self-healing ADB loopback verified"
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isPaired || !hasSecureSettings) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSetup() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tap to complete Setup Wizard",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveProfileSummaryCard(
    profile: DisplayProfile,
    onOpenEditor: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenEditor() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active Profile: ${profile.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${profile.width} × ${profile.height} @ ${profile.dpi} DPI (${profile.logicalWidthDp}×${profile.logicalHeightDp} dp)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${profile.maxFps} FPS • ${profile.bitRateMbps} Mbps • ${profile.codec.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.Default.AspectRatio,
                contentDescription = "Edit Profile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProfileSelectionItem(
    profile: DisplayProfile,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${profile.width}x${profile.height} • ${profile.dpi} DPI",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
