package org.localdex.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.localdex.core.adb.discovery.AdbDiscoveryManager
import org.localdex.core.adb.permission.AdbPermissionManager
import org.localdex.core.scrcpy.model.DisplayProfile
import org.localdex.data.DisplayProfileRepository
import org.localdex.service.DexSessionService
import org.localdex.service.SessionState
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

    private var onPermissionUpdated: (() -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onPermissionUpdated?.invoke()
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        discoveryManager = AdbDiscoveryManager(this)
        profileRepository = DisplayProfileRepository(this)

        setContent {
            LocalDeXTheme {
                val coroutineScope = rememberCoroutineScope()
                val lifecycleOwner = LocalLifecycleOwner.current
                val sessionState by DexSessionService.sessionState.collectAsState()
                val discoveryState by discoveryManager.discoveryState.collectAsState()
                val activeProfile by profileRepository.selectedProfileFlow.collectAsState(initial = DisplayProfile.PRESET_1080P)
                val customProfile by profileRepository.customProfileFlow.collectAsState(initial = null)
                val allProfiles = remember(customProfile) {
                    val custom = customProfile
                    if (custom != null) DisplayProfile.DEFAULT_PRESETS + custom else DisplayProfile.DEFAULT_PRESETS
                }

                val postureTracker = remember { FoldablePostureTracker(this@MainActivity) }
                val foldableState by postureTracker.postureFlow.collectAsState(initial = FoldableState())

                var isSetupOpen by remember { mutableStateOf(false) }
                var isProfileEditorOpen by remember { mutableStateOf(false) }
                var pendingViewerLaunch by remember { mutableStateOf(false) }

                var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission()) }
                var isDevOptions by remember { mutableStateOf(AdbPermissionManager.isDeveloperOptionsEnabled(this@MainActivity)) }
                var isWirelessAdb by remember { mutableStateOf(AdbPermissionManager.isWirelessDebuggingEnabled(this@MainActivity)) }
                var hasSecureSettings by remember { mutableStateOf(AdbPermissionManager.hasSecureSettingsPermission(this@MainActivity)) }
                var isFreeform by remember { mutableStateOf(AdbPermissionManager.isFreeformSupportEnabled(this@MainActivity)) }
                var isForceResizable by remember { mutableStateOf(AdbPermissionManager.isForceResizableActivitiesEnabled(this@MainActivity)) }
                var isDesktopMode by remember { mutableStateOf(AdbPermissionManager.isForceDesktopModeOnExternalDisplaysEnabled(this@MainActivity)) }

                fun refreshSystemStates() {
                    hasNotificationPermission = checkNotificationPermission()
                    isDevOptions = AdbPermissionManager.isDeveloperOptionsEnabled(this@MainActivity)
                    isWirelessAdb = AdbPermissionManager.isWirelessDebuggingEnabled(this@MainActivity)
                    hasSecureSettings = AdbPermissionManager.hasSecureSettingsPermission(this@MainActivity)
                    isFreeform = AdbPermissionManager.isFreeformSupportEnabled(this@MainActivity)
                    isForceResizable = AdbPermissionManager.isForceResizableActivitiesEnabled(this@MainActivity)
                    isDesktopMode = AdbPermissionManager.isForceDesktopModeOnExternalDisplaysEnabled(this@MainActivity)
                }

                onPermissionUpdated = { refreshSystemStates() }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshSystemStates()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        onPermissionUpdated = null
                    }
                }

                LaunchedEffect(sessionState) {
                    if (pendingViewerLaunch && sessionState is SessionState.Running) {
                        pendingViewerLaunch = false
                        startActivity(
                            Intent(this@MainActivity, DexViewerActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                        )
                    } else if (sessionState is SessionState.Error) {
                        pendingViewerLaunch = false
                    }
                }

                val setupSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                val isPaired = hasSecureSettings || discoveryState.connectEndpoint != null

                HomeScreen(
                    sessionState = sessionState,
                    isPaired = isPaired,
                    hasSecureSettings = hasSecureSettings,
                    activeProfile = activeProfile,
                    allProfiles = allProfiles,
                    foldableState = foldableState,
                    onSelectProfile = { profile ->
                        coroutineScope.launch {
                            profileRepository.selectProfile(profile.id)
                        }
                    },
                    onStartDeX = {
                        coroutineScope.launch {
                            DexSessionService.activeProfile = activeProfile
                            var connectPort = discoveryManager.discoveryState.value.connectEndpoint?.port
                            if (connectPort == null || connectPort <= 0) {
                                AdbPermissionManager.ensureWirelessDebuggingEnabled(this@MainActivity)
                                discoveryManager.startDiscovery()
                                connectPort = withTimeoutOrNull(3000L) {
                                    discoveryManager.discoveryState
                                        .mapNotNull { it.connectEndpoint?.port }
                                        .first { it > 0 }
                                }
                            }

                            if (connectPort == null || connectPort <= 0) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Wireless Debugging port not found via mDNS. Enable Wireless Debugging in Setup.",
                                    Toast.LENGTH_LONG
                                ).show()
                                isSetupOpen = true
                                return@launch
                            }

                            pendingViewerLaunch = true
                            val intent = Intent(this@MainActivity, DexSessionService::class.java).apply {
                                action = DexSessionService.ACTION_START_SESSION
                                putExtra(DexSessionService.EXTRA_CONNECT_PORT, connectPort)
                            }
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        }
                    },
                    onStopDeX = {
                        pendingViewerLaunch = false
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
                        isForceResizableEnabled = isForceResizable,
                        isDesktopModeEnabled = isDesktopMode,
                        onToggleFreeform = { enabled ->
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    AdbPermissionManager.setFreeformSupport(this@MainActivity, null, enabled)
                                }
                                isFreeform = AdbPermissionManager.isFreeformSupportEnabled(this@MainActivity)
                            }
                        },
                        onToggleForceResizable = { enabled ->
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    AdbPermissionManager.setForceResizableActivities(this@MainActivity, null, enabled)
                                }
                                isForceResizable = AdbPermissionManager.isForceResizableActivitiesEnabled(this@MainActivity)
                            }
                        },
                        onToggleDesktopMode = { enabled ->
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    AdbPermissionManager.setDesktopModeDebug(this@MainActivity, null, enabled)
                                }
                                isDesktopMode = AdbPermissionManager.isForceDesktopModeOnExternalDisplaysEnabled(this@MainActivity)
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
