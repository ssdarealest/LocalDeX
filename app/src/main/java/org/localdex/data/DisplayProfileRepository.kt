package org.localdex.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.localdex.core.scrcpy.input.InputMode
import org.localdex.core.scrcpy.model.DisplayProfile

private val Context.dataStore by preferencesDataStore(name = "localdex_preferences")

class DisplayProfileRepository(private val context: Context) {

    companion object {
        private val KEY_SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        private val KEY_CUSTOM_WIDTH = intPreferencesKey("custom_width")
        private val KEY_CUSTOM_HEIGHT = intPreferencesKey("custom_height")
        private val KEY_CUSTOM_DPI = intPreferencesKey("custom_dpi")
        private val KEY_CUSTOM_FPS = intPreferencesKey("custom_fps")
        private val KEY_CUSTOM_BITRATE = intPreferencesKey("custom_bitrate")
        private val KEY_INPUT_MODE = stringPreferencesKey("input_mode")
    }

    val selectedProfileFlow: Flow<DisplayProfile> = context.dataStore.data.map { prefs ->
        val profileId = prefs[KEY_SELECTED_PROFILE_ID] ?: DisplayProfile.PRESET_1080P.id
        val preset = DisplayProfile.DEFAULT_PRESETS.find { it.id == profileId }
        if (preset != null) {
            preset
        } else {
            DisplayProfile(
                id = "custom",
                name = "Custom",
                width = prefs[KEY_CUSTOM_WIDTH] ?: 1920,
                height = prefs[KEY_CUSTOM_HEIGHT] ?: 1080,
                dpi = prefs[KEY_CUSTOM_DPI] ?: 240,
                maxFps = prefs[KEY_CUSTOM_FPS] ?: 60,
                bitRateMbps = prefs[KEY_CUSTOM_BITRATE] ?: 16,
                isCustom = true
            )
        }
    }

    val inputModeFlow: Flow<InputMode> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_INPUT_MODE]) {
            InputMode.DIRECT_TOUCH.name -> InputMode.DIRECT_TOUCH
            else -> InputMode.TRACKPAD
        }
    }

    suspend fun selectProfile(profileId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_PROFILE_ID] = profileId
        }
    }

    suspend fun saveCustomProfile(profile: DisplayProfile) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_PROFILE_ID] = "custom"
            prefs[KEY_CUSTOM_WIDTH] = profile.width
            prefs[KEY_CUSTOM_HEIGHT] = profile.height
            prefs[KEY_CUSTOM_DPI] = profile.dpi
            prefs[KEY_CUSTOM_FPS] = profile.maxFps
            prefs[KEY_CUSTOM_BITRATE] = profile.bitRateMbps
        }
    }

    suspend fun saveInputMode(mode: InputMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INPUT_MODE] = mode.name
        }
    }
}
