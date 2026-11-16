package org.localdex

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class LocalDeXApp : Application() {

    companion object {
        const val CHANNEL_SESSION_ID = "localdex_session"
        const val CHANNEL_PAIRING_ID = "localdex_pairing"
        lateinit var instance: LocalDeXApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        setupSecurityProviders()
        setupNotificationChannels()
    }

    private fun setupSecurityProviders() {
        // Insert BouncyCastle provider for SPAKE2+ and TLS 1.3 pairing
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }

    private fun setupNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val sessionChannel = NotificationChannel(
                CHANNEL_SESSION_ID,
                getString(R.string.channel_session_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_session_desc)
                setShowBadge(false)
            }

            val pairingChannel = NotificationChannel(
                CHANNEL_PAIRING_ID,
                getString(R.string.channel_pairing_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_pairing_desc)
                setShowBadge(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(listOf(sessionChannel, pairingChannel))
        }
    }
}
