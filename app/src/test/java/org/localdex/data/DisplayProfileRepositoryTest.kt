package org.localdex.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.localdex.core.scrcpy.input.InputMode
import org.localdex.core.scrcpy.model.DisplayProfile

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayProfileRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(scope: TestScope): DisplayProfileRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { tempFolder.newFile("localdex_prefs_${System.nanoTime()}.preferences_pb") }
        )
        return DisplayProfileRepository(dataStore)
    }

    @Test
    fun defaults_return1080pNoCustomAndTrackpadInputMode() = runTest(UnconfinedTestDispatcher()) {
        val repo = createRepository(this)

        assertThat(repo.selectedProfileFlow.first()).isEqualTo(DisplayProfile.PRESET_1080P)
        assertThat(repo.customProfileFlow.first()).isNull()
        assertThat(repo.inputModeFlow.first()).isEqualTo(InputMode.TRACKPAD)
    }

    @Test
    fun selectProfile_switchesActivePresetWithoutCreatingCustomProfile() = runTest(UnconfinedTestDispatcher()) {
        val repo = createRepository(this)

        repo.selectProfile(DisplayProfile.PRESET_FOLD_INNER.id)

        assertThat(repo.selectedProfileFlow.first()).isEqualTo(DisplayProfile.PRESET_FOLD_INNER)
        assertThat(repo.customProfileFlow.first()).isNull()
    }

    @Test
    fun saveCustomProfile_persistsFieldsAndRemainsAvailableAfterPresetSwitch() = runTest(UnconfinedTestDispatcher()) {
        val repo = createRepository(this)
        val custom = DisplayProfile(
            id = "custom",
            name = "Custom",
            width = 2560,
            height = 1440,
            dpi = 300,
            maxFps = 120,
            bitRateMbps = 28,
            codec = DisplayProfile.VideoCodec.H265,
            audioEnabled = true,
            audioCodec = DisplayProfile.AudioCodec.AAC,
            isCustom = true
        )

        repo.saveCustomProfile(custom)

        val loaded = repo.selectedProfileFlow.first()
        assertThat(loaded).isEqualTo(custom)
        assertThat(repo.customProfileFlow.first()).isEqualTo(custom)

        repo.selectProfile(DisplayProfile.PRESET_ULTRAWIDE.id)
        assertThat(repo.selectedProfileFlow.first()).isEqualTo(DisplayProfile.PRESET_ULTRAWIDE)
        assertThat(repo.customProfileFlow.first()).isEqualTo(custom)
    }

    @Test
    fun saveInputMode_persistsDirectTouchAndTrackpadTransitions() = runTest(UnconfinedTestDispatcher()) {
        val repo = createRepository(this)

        repo.saveInputMode(InputMode.DIRECT_TOUCH)
        assertThat(repo.inputModeFlow.first()).isEqualTo(InputMode.DIRECT_TOUCH)

        repo.saveInputMode(InputMode.TRACKPAD)
        assertThat(repo.inputModeFlow.first()).isEqualTo(InputMode.TRACKPAD)
    }
}
