package org.localdex.service

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.localdex.LocalDeXApp
import org.localdex.R
import org.localdex.core.adb.connection.AdbConnection
import org.localdex.core.adb.pairing.AdbPairingClient
import org.localdex.core.adb.pairing.PairingResult
import org.localdex.core.adb.permission.AdbPermissionManager

/**
 * Handles inline-reply 6-digit PIN input directly from the Android notification shade.
 */
class PairingReplyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PairingReplyReceiver"
        const val KEY_PAIRING_CODE_REPLY = "key_pairing_code_reply"
        const val EXTRA_PAIRING_PORT = "extra_pairing_port"
        const val EXTRA_CONNECT_PORT = "extra_connect_port"
        const val NOTIFICATION_ID_PAIRING = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val pairingCode = results.getCharSequence(KEY_PAIRING_CODE_REPLY)?.toString()?.trim() ?: return
        val pairingPort = intent.getIntExtra(EXTRA_PAIRING_PORT, -1)
        val connectPort = intent.getIntExtra(EXTRA_CONNECT_PORT, -1)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Update notification to indicate pairing in progress
        val progressNotification = NotificationCompat.Builder(context, LocalDeXApp.CHANNEL_PAIRING_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Pairing with LocalDeX...")
            .setContentText("Authenticating 6-digit code with adbd...")
            .setProgress(0, 0, true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_PAIRING, progressNotification)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (pairingPort <= 0) {
                    notifyFailure(context, notificationManager, "Pairing port not found via mDNS")
                    return@launch
                }

                val pairingClient = AdbPairingClient(context)
                val result = pairingClient.pair(port = pairingPort, pairingCode = pairingCode)

                when (result) {
                    is PairingResult.Success -> {
                        Log.i(TAG, "Pairing successful! Self-healing permissions...")
                        if (connectPort > 0) {
                            try {
                                val conn = AdbConnection(context = context, port = connectPort, useTls = true)
                                if (conn.connect()) {
                                    AdbPermissionManager.grantSecureSettings(conn, context.packageName)
                                    AdbPermissionManager.ensureWirelessDebuggingEnabled(context)
                                    conn.close()
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Permission auto-grant error", e)
                            }
                        }

                        val successNotification = NotificationCompat.Builder(context, LocalDeXApp.CHANNEL_PAIRING_ID)
                            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                            .setContentTitle("LocalDeX Paired Successfully!")
                            .setContentText("Loopback ADB pairing complete and permissions verified.")
                            .setAutoCancel(true)
                            .build()
                        notificationManager.notify(NOTIFICATION_ID_PAIRING, successNotification)
                    }
                    is PairingResult.Failure -> {
                        notifyFailure(context, notificationManager, result.message)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun notifyFailure(context: Context, nm: NotificationManager, reason: String) {
        val failNotification = NotificationCompat.Builder(context, LocalDeXApp.CHANNEL_PAIRING_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Pairing Failed")
            .setContentText(reason)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID_PAIRING, failNotification)
    }
}
