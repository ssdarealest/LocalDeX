package org.localdex.core.adb.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import org.localdex.core.adb.connection.AdbConnection

/**
 * Manages Developer Options detection, Wireless Debugging state verification,
 * and self-healing WRITE_SECURE_SETTINGS automation.
 */
object AdbPermissionManager {

    private const val TAG = "AdbPermissionManager"
    private const val SETTING_ADB_WIFI_ENABLED = "adb_wifi_enabled"

    /**
     * Checks if the app currently holds android.permission.WRITE_SECURE_SETTINGS.
     */
    fun hasSecureSettingsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks whether Developer Options is enabled on this device.
     */
    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks whether Wireless Debugging is actively toggled on in system settings.
     */
    fun isWirelessDebuggingEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                SETTING_ADB_WIFI_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Re-enables Wireless Debugging automatically using Settings.Global if WRITE_SECURE_SETTINGS is held.
     * Prevents dropouts after phone reboot or Wi-Fi network switching.
     */
    fun ensureWirelessDebuggingEnabled(context: Context): Boolean {
        if (!hasSecureSettingsPermission(context)) {
            Log.w(TAG, "Cannot ensure wireless debugging: WRITE_SECURE_SETTINGS not held")
            return false
        }

        return try {
            val enabled = Settings.Global.putInt(
                context.contentResolver,
                SETTING_ADB_WIFI_ENABLED,
                1
            )
            Log.i(TAG, "Self-healing: toggled adb_wifi_enabled = 1 (result: $enabled)")
            enabled
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to self-heal adb_wifi_enabled", e)
            false
        }
    }

    /**
     * Grants WRITE_SECURE_SETTINGS to LocalDeX via loopback ADB shell.
     */
    suspend fun grantSecureSettings(connection: AdbConnection, packageName: String): Result<Unit> {
        return try {
            val command = "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
            Log.i(TAG, "Executing loopback grant: $command")
            val output = connection.executeShell(command)
            if (output.isBlank() || output.contains("Success", ignoreCase = true)) {
                Log.i(TAG, "Successfully granted WRITE_SECURE_SETTINGS")
                Result.success(Unit)
            } else {
                Log.e(TAG, "pm grant output error: $output")
                Result.failure(Exception("Failed to grant permission: $output"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during pm grant", e)
            Result.failure(e)
        }
    }

    /**
     * Optional One UI 8.5+ enhancement: enables freeform window support and forces resizable activities.
     */
    suspend fun enableFreeformWindows(connection: AdbConnection): Boolean {
        return try {
            connection.executeShell("settings put global enable_freeform_support 1")
            connection.executeShell("settings put global force_resizable_activities 1")
            Log.i(TAG, "One UI freeform window support enabled")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to configure freeform window settings", e)
            false
        }
    }
}
