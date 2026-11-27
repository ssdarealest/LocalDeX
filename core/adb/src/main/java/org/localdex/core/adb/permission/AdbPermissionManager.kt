package org.localdex.core.adb.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.localdex.core.adb.connection.AdbConnection

/**
 * Manages Developer Options detection, Wireless Debugging state verification,
 * and self-healing WRITE_SECURE_SETTINGS automation.
 */
object AdbPermissionManager {

    private const val TAG = "AdbPermissionManager"
    private const val SETTING_ADB_WIFI_ENABLED = "adb_wifi_enabled"
    const val SETTING_ENABLE_FREEFORM_SUPPORT = "enable_freeform_support"
    const val SETTING_FORCE_RESIZABLE_ACTIVITIES = "force_resizable_activities"
    const val SETTING_FORCE_DESKTOP_MODE_ON_EXTERNAL = "force_desktop_mode_on_external_displays"

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
     * Checks if One UI freeform window support is enabled.
     */
    fun isFreeformSupportEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, SETTING_ENABLE_FREEFORM_SUPPORT, 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if forcing resizable activities is enabled in developer settings.
     */
    fun isForceResizableActivitiesEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, SETTING_FORCE_RESIZABLE_ACTIVITIES, 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if forcing desktop mode on external displays is enabled.
     */
    fun isForceDesktopModeOnExternalDisplaysEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, SETTING_FORCE_DESKTOP_MODE_ON_EXTERNAL, 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun writeGlobalIntSetting(
        context: Context,
        connection: AdbConnection?,
        settingKey: String,
        enabled: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val intVal = if (enabled) 1 else 0
        var success = false
        if (hasSecureSettingsPermission(context)) {
            try {
                success = Settings.Global.putInt(context.contentResolver, settingKey, intVal)
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException updating $settingKey via ContentResolver", e)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update $settingKey via ContentResolver", e)
            }
        }
        if (connection != null) {
            try {
                connection.executeShell("settings put global $settingKey $intVal")
                success = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update $settingKey via ADB", e)
            }
        }
        success
    }

    /**
     * Enables or disables One UI freeform window support.
     */
    suspend fun setFreeformSupport(context: Context, connection: AdbConnection?, enabled: Boolean): Boolean =
        writeGlobalIntSetting(context, connection, SETTING_ENABLE_FREEFORM_SUPPORT, enabled)

    /**
     * Enables or disables force resizable activities.
     */
    suspend fun setForceResizableActivities(context: Context, connection: AdbConnection?, enabled: Boolean): Boolean =
        writeGlobalIntSetting(context, connection, SETTING_FORCE_RESIZABLE_ACTIVITIES, enabled)

    /**
     * Sets desktop mode debug flags via ContentResolver (when WRITE_SECURE_SETTINGS is held)
     * and optionally loopback ADB shell (`setprop` + `settings put global`).
     */
    suspend fun setDesktopModeDebug(context: Context, connection: AdbConnection?, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            var success = writeGlobalIntSetting(
                context = context,
                connection = connection,
                settingKey = SETTING_FORCE_DESKTOP_MODE_ON_EXTERNAL,
                enabled = enabled
            )
            if (connection != null) {
                val intVal = if (enabled) 1 else 0
                try {
                    connection.executeShell("setprop persist.wm.debug.desktop_mode $intVal")
                    success = true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set persist.wm.debug.desktop_mode via ADB", e)
                }
            }
            success
        }

    /**
     * Sets desktop mode debug system property and global flag.
     */
    suspend fun setDesktopModeDebug(connection: AdbConnection, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val intVal = if (enabled) 1 else 0
        return@withContext try {
            connection.executeShell("setprop persist.wm.debug.desktop_mode $intVal")
            connection.executeShell("settings put global $SETTING_FORCE_DESKTOP_MODE_ON_EXTERNAL $intVal")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set desktop mode debug flags", e)
            false
        }
    }

    /**
     * Optional One UI 8.5+ enhancement: enables freeform window support and forces resizable activities.
     */
    suspend fun enableFreeformWindows(connection: AdbConnection): Boolean {
        return try {
            connection.executeShell("settings put global $SETTING_ENABLE_FREEFORM_SUPPORT 1")
            connection.executeShell("settings put global $SETTING_FORCE_RESIZABLE_ACTIVITIES 1")
            Log.i(TAG, "One UI freeform window support enabled")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to configure freeform window settings", e)
            false
        }
    }
}
