package org.localdex.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.localdex.core.adb.discovery.AdbDiscoveryManager
import org.localdex.core.adb.permission.AdbPermissionManager
import org.localdex.core.scrcpy.model.DisplayProfile
import org.localdex.data.DisplayProfileRepository
import org.localdex.service.DexSessionService
import org.localdex.ui.foldable.FoldablePostureTracker
import org.localdex.ui.foldable.FoldableState
import org.localdex.ui.home.HomeScreen
import org.localdex.ui.profile.ProfileEditorSheet
import org.localdex.ui.setup.SetupWizardSheet
import org.localdex.ui.theme.LocalDeXTheme
import org.localdex.ui.viewer.DexViewerActivity

class MainActivity : ComponentActivity() {

    private lateinit var discoveryManager: AdbDiscoveryManager
    private lateinit var profileRepository: DisplayProfileRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled dynamically in state */ }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        discoveryManager = AdbDiscoveryManager(this)
        profileRepository = DisplayProfileRepository(this)

        setContent {
            LocalDeXTheme {
                val coroutineScope = rememberCoroutineScope()
                val sessionState by DexSessionService.sessionState.collectAsState()
                val discoveryState by discoveryManager.discoveryState.collectAsState()
                val activeProfile by profileRepository.selectedProfileFlow.collectAsState(initial = DisplayProfile.PRESET_1080P)

                val postureTracker = remember { FoldablePostureTracker(this@MainActivity) }
                val foldableState by postureTracker.postureFlow.collectAsState(initial = FoldableState())

                var isSetupOpen by remember { mutableStateOf(false) }
                var isProfileEditorOpen by remember { mutableStateOf(false) }
                var isFreeform by remember { mutableStateOf(AdbPermissionManager.isFreeformSupportEnabled(this@MainActivity)) }
                var isDesktopMode by remember { mutableStateOf(AdbPermissionManager.isForceDesktopModeOnExternalDisplaysEnabled(this@MainActivity)) }

                val setupSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else true

                val isDevOptions = AdbPermissionManager.isDeveloperOptionsEnabled(this@MainActivity)
                val isWirelessAdb = AdbPermissionManager.isWirelessDebuggingEnabled(this@MainActivity)
                val hasSecureSettings = AdbPermissionManager.hasSecureSettingsPermission(this@MainActivity)
                val isPaired = hasSecureSettings || discoveryState.connectEndpoint != null

                HomeScreen(
                    sessionState = sessionState,
                    isPaired = isPaired,
                    hasSecureSettings = hasSecureSettings,
                    activeProfile = activeProfile,
                    allProfiles = DisplayProfile.DEFAULT_PRESETS,
                    foldableState = foldableState,
                    onSelectProfile = { profile ->
                        coroutineScope.launch {
                            profileRepository.selectProfile(profile.id)
                        }
                    },
                    onStartDeX = {
                        DexSessionService.activeProfile = activeProfile
                        val connectPort = discoveryState.connectEndpoint?.port ?: 5555
                        val intent = Intent(this@MainActivity, DexSessionService::class.java).apply {
                            action = DexSessionService.ACTION_START_SESSION
                            putExtra(DexSessionService.EXTRA_CONNECT_PORT, connectPort)
                        }
                        startService(intent)
                    },
                    onStopDeX = {
                        val intent = Intent(this@MainActivity, DexSessionService::class.java).apply {
                            action = DexSessionService.ACTION_STOP_SESSION
                        }
                        startService(intent)
                    },
                    onResumeDeX = {
                        val viewerIntent = Intent(this@MainActivity, DexViewerActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(viewerIntent)
                    },
                    onOpenSetup = { isSetupOpen = true },
                    onOpenProfileEditor = { isProfileEditorOpen = true }
                )

                if (isSetupOpen) {
                    SetupWizardSheet(
                        sheetState = setupSheetState,
                        hasNotificationPermission = hasNotificationPermission,
                        isDeveloperOptionsEnabled = isDevOptions,
                        isWirelessDebuggingEnabled = isWirelessAdb,
                        hasSecureSettingsPermission = hasSecureSettings,
                        discoveredPairingPort = discoveryState.pairingEndpoint?.port,
                        discoveredConnectPort = discoveryState.connectEndpoint?.port,
                        isFreeformEnabled = isFreeform,
                        isDesktopModeEnabled = isDesktopMode,
                        onToggleFreeform = { enabled ->
                            coroutineScope.launch {
                                AdbPermissionManager.setFreeformSupport(this@MainActivity, null, enabled)
                                isFreeform = enabled
                            }
                        },
                        onToggleDesktopMode = { enabled ->
                            coroutineScope.launch {
                                if (AdbPermissionManager.hasSecureSettingsPermission(this@MainActivity)) {
                                    android.provider.Settings.Global.putInt(
                                        contentResolver,
                                        AdbPermissionManager.SETTING_FORCE_DESKTOP_MODE_ON_EXTERNAL,
                                        if (enabled) 1 else 0
                                    )
                                }
                                isDesktopMode = enabled
                            }
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onTriggerPairingNotification = { pairingPort, connectPort ->
                            val intent = Intent(this@MainActivity, DexSessionService::class.java).apply {
                                action = DexSessionService.ACTION_SHOW_PAIRING_NOTIFICATION
                                putExtra(DexSessionService.EXTRA_PAIRING_PORT, pairingPort)
                                putExtra(DexSessionService.EXTRA_CONNECT_PORT, connectPort)
                            }
                            startService(intent)
                        },
                        onDismiss = { isSetupOpen = false }
                    )
                }

                if (isProfileEditorOpen) {
                    ProfileEditorSheet(
                        sheetState = profileSheetState,
                        initialProfile = activeProfile,
                        onSaveProfile = { newProfile ->
                            coroutineScope.launch {
                                profileRepository.saveCustomProfile(newProfile)
                            }
                        },
                        onDismiss = { isProfileEditorOpen = false }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        discoveryManager.startDiscovery()
        AdbPermissionManager.ensureWirelessDebuggingEnabled(this)
    }

    override fun onPause() {
        super.onPause()
        discoveryManager.stopDiscovery()
    }
}
