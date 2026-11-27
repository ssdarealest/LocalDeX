package org.localdex.core.scrcpy

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.localdex.core.scrcpy.model.DisplayProfile
import org.localdex.core.scrcpy.server.ScrcpyServerManager

class ScrcpyServerManagerTest {

    @Test
    fun buildServerCommand_defaultProfile_disablesAudioAndFormatsVideoCorrectly() {
        val profile = DisplayProfile.PRESET_1080P
        val command = ScrcpyServerManager.buildServerCommand(profile)

        assertThat(command).contains("new_display=1920x1080/240")
        assertThat(command).contains("video_codec=h264")
        assertThat(command).contains("video_bit_rate=16000000")
        assertThat(command).contains("max_fps=60")
        assertThat(command).contains("audio=false")
        assertThat(command).contains("control=true")
        assertThat(command).contains("port=27183")
    }

    @Test
    fun buildServerCommand_audioEnabled_formatsAudioCodec() {
        val profile = DisplayProfile.PRESET_1080P.copy(
            audioEnabled = true,
            audioCodec = DisplayProfile.AudioCodec.OPUS
        )
        val command = ScrcpyServerManager.buildServerCommand(profile)

        assertThat(command).contains("audio=true")
        assertThat(command).contains("audio_codec=opus")
    }

    @Test
    fun buildServerCommand_h265Codec_configuresHevc() {
        val profile = DisplayProfile.PRESET_1080P.copy(
            codec = DisplayProfile.VideoCodec.H265
        )
        val command = ScrcpyServerManager.buildServerCommand(profile)

        assertThat(command).contains("video_codec=h265")
    }

    @Test
    fun buildServerCommand_foldInner_formatsCorrectDisplayArgument() {
        val profile = DisplayProfile.PRESET_FOLD_INNER
        val command = ScrcpyServerManager.buildServerCommand(profile)

        assertThat(command).contains("new_display=2176x1812/360")
        assertThat(command).contains("video_bit_rate=24000000")
    }
}
