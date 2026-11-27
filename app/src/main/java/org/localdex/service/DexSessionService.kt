package org.localdex.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.localdex.LocalDeXApp
import org.localdex.R
import org.localdex.core.adb.connection.AdbConnection
import org.localdex.core.scrcpy.model.DisplayProfile
import org.localdex.core.scrcpy.server.ScrcpyServerManager
import org.localdex.core.scrcpy.server.ScrcpySessionInfo
import org.localdex.ui.MainActivity
import org.localdex.ui.viewer.DexViewerActivity

sealed class SessionState {
    data object Idle : SessionState()
    data object Starting : SessionState()
    data class Running(val sessionInfo: ScrcpySessionInfo) : SessionState()
    data class Error(val message: String) : SessionState()
}

/**
 * Foreground service that keeps Samsung DeX session, virtual display, and ADB daemon stream alive.
 */
class DexSessionService : Service() {

    inner class LocalBinder : Binder() {
        val service: DexSessionService get() = this@DexSessionService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var adbConnection: AdbConnection? = null
    private var serverManager: ScrcpyServerManager? = null
    private var sessionInfoJob: Job? = null

    companion object {
        private const val TAG = "DexSessionService"
        private const val NOTIFICATION_ID_SESSION = 1001

        const val ACTION_START_SESSION = "org.localdex.ACTION_START_SESSION"
        const val ACTION_STOP_SESSION = "org.localdex.ACTION_STOP_SESSION"
        const val ACTION_SHOW_PAIRING_NOTIFICATION = "org.localdex.ACTION_SHOW_PAIRING_NOTIFICATION"

        const val EXTRA_CONNECT_PORT = "extra_connect_port"
        const val EXTRA_PAIRING_PORT = "extra_pairing_port"

        private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
        val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        var activeProfile: DisplayProfile = DisplayProfile.PRESET_1080P
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val connectPort = intent.getIntExtra(EXTRA_CONNECT_PORT, -1)
                startDexSession(connectPort)
            }
            ACTION_STOP_SESSION -> {
                stopDexSession()
            }
            ACTION_SHOW_PAIRING_NOTIFICATION -> {
                val pairingPort = intent.getIntExtra(EXTRA_PAIRING_PORT, -1)
                val connectPort = intent.getIntExtra(EXTRA_CONNECT_PORT, -1)
                showPairingNotification(pairingPort, connectPort)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDexSession(connectPort: Int) {
        if (_sessionState.value is SessionState.Running || _sessionState.value is SessionState.Starting) return

        _sessionState.value = SessionState.Starting
        val foregroundTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID_SESSION, buildSessionNotification(null), foregroundTypes)

        scope.launch {
            try {
                if (connectPort <= 0) {
                    failAndStop("Invalid connect port ($connectPort)")
                    return@launch
                }

                val conn = AdbConnection(this@DexSessionService, port = connectPort, useTls = true)
                if (!conn.connect()) {
                    failAndStop("Failed to connect to loopback ADB")
                    return@launch
                }
                adbConnection = conn

                val sm = ScrcpyServerManager(this@DexSessionService, conn)
                serverManager = sm

                if (!sm.ensureServerDeployed()) {
                    failAndStop("Failed to deploy scrcpy-server binary")
                    return@launch
                }

                val info = sm.startServer(activeProfile)
                _sessionState.value = SessionState.Running(info)

                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NOTIFICATION_ID_SESSION, buildSessionNotification(info))

                sessionInfoJob?.cancel()
                sessionInfoJob = scope.launch {
                    sm.sessionInfo.collect { updated ->
                        if (updated != null && _sessionState.value is SessionState.Running) {
                            _sessionState.value = SessionState.Running(updated)
                            nm.notify(NOTIFICATION_ID_SESSION, buildSessionNotification(updated))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting DeX session", e)
                failAndStop(e.message ?: "Unknown error")
            }
        }
    }

    private fun failAndStop(message: String) {
        _sessionState.value = SessionState.Error(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun stopDexSession() {
        sessionInfoJob?.cancel()
        sessionInfoJob = null

        serverManager?.stopServer()
        serverManager = null

        adbConnection?.close()
        adbConnection = null

        _sessionState.value = SessionState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildSessionNotification(info: ScrcpySessionInfo?): Notification {
        val resumeIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DexViewerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DexSessionService::class.java).apply { action = ACTION_STOP_SESSION },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (info != null) {
            "DeX Active on Display #${info.displayId} (${info.profile.width}x${info.profile.height})"
        } else {
            "Starting LocalDeX Session..."
        }

        val builder = NotificationCompat.Builder(this, LocalDeXApp.CHANNEL_SESSION_ID)
            .setSmallIcon(R.drawable.ic_dex_notification)
            .setContentTitle(title)
            .setContentText("Tap to open fullscreen viewer")
            .setContentIntent(resumeIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_play, getString(R.string.session_action_resume), resumeIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.session_action_stop), stopIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    fun showPairingNotification(pairingPort: Int, connectPort: Int) {
        val remoteInput = RemoteInput.Builder(PairingReplyReceiver.KEY_PAIRING_CODE_REPLY)
            .setLabel("6-digit code")
            .build()

        val replyIntent = Intent(this, PairingReplyReceiver::class.java).apply {
            action = "org.localdex.ACTION_PAIRING_REPLY"
            putExtra(PairingReplyReceiver.EXTRA_PAIRING_PORT, pairingPort)
            putExtra(PairingReplyReceiver.EXTRA_CONNECT_PORT, connectPort)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            getString(R.string.pairing_action_reply),
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(this, LocalDeXApp.CHANNEL_PAIRING_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(getString(R.string.pairing_notification_title))
            .setContentText("Pairing port: $pairingPort. Enter code to complete.")
            .addAction(action)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(PairingReplyReceiver.NOTIFICATION_ID_PAIRING, notification)
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalDeX:SessionWakeLock").apply {
            acquire(4 * 60 * 60 * 1000L) // 4 hours max safety
        }

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LocalDeX:WifiLock").apply {
            acquire()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDexSession()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }
}
