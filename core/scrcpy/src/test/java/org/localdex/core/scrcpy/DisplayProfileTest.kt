package org.localdex.core.scrcpy

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.localdex.core.scrcpy.model.DisplayProfile

class DisplayProfileTest {

    @Test
    fun toNewDisplayArg_formatsCorrectWidthHeightAndDpi() {
        val profile = DisplayProfile(
            id = "test",
            name = "Test 1080p",
            width = 1920,
            height = 1080,
            dpi = 240
        )
        assertThat(profile.toNewDisplayArg()).isEqualTo("1920x1080/240")
    }

    @Test
    fun logicalDimensionsDp_calculatesCorrectDensityIndependentPixels() {
        // 1920 / (240 / 160) = 1920 / 1.5 = 1280 dp
        // 1080 / (240 / 160) = 1080 / 1.5 = 720 dp
        val profile = DisplayProfile.PRESET_1080P
        assertThat(profile.logicalWidthDp).isEqualTo(1280)
        assertThat(profile.logicalHeightDp).isEqualTo(720)
    }

    @Test
    fun aspectRatio_calculatesAccurately() {
        val profile16_9 = DisplayProfile.PRESET_1080P
        assertThat(profile16_9.aspectRatio).isWithin(0.01f).of(16f / 9f)

        val profile4_3 = DisplayProfile.PRESET_4_3
        assertThat(profile4_3.aspectRatio).isWithin(0.01f).of(4f / 3f)
    }
}
