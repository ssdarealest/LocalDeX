package org.localdex.core.scrcpy

import android.content.Context
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.localdex.core.adb.connection.AdbConnection
import org.localdex.core.adb.connection.AdbStream
import org.localdex.core.scrcpy.model.DisplayProfile
import org.localdex.core.scrcpy.server.ScrcpyServerManager
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

class ScrcpyServerManagerTest {

    @Test
    fun buildServerCommand_defaultProfile_usesForwardTunnelAndDummyByte() {
        val profile = DisplayProfile.PRESET_1080P
        val command = ScrcpyServerManager.buildServerCommand(profile)

        assertThat(command).contains("scid=-1")
        assertThat(command).contains("tunnel_forward=true")
        assertThat(command).contains("send_dummy_byte=true")
        assertThat(command).doesNotContain("port=")
        assertThat(command).contains("new_display=1920x1080/240")
        assertThat(command).contains("video_codec=h264")
        assertThat(command).contains("video_bit_rate=16000000")
        assertThat(command).contains("max_fps=60")
        assertThat(command).contains("audio=false")
        assertThat(command).contains("control=true")
    }

    @Test
    fun buildServerCommand_andSocketDestination_formatNonNegativeScidAsHex() {
        assertThat(ScrcpyServerManager.socketDestination(-1)).isEqualTo("localabstract:scrcpy")
        assertThat(ScrcpyServerManager.socketDestination(0x12ab))
            .isEqualTo("localabstract:scrcpy_000012ab")

        val command = ScrcpyServerManager.buildServerCommand(DisplayProfile.PRESET_1080P, scid = 0x12ab)
        assertThat(command).contains("scid=12ab")
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

    @Test
    fun parseDisplayId_ignoresResolutionAndExtractsValidDisplayId() {
        assertThat(ScrcpyServerManager.parseDisplayId("[server] INFO: New display: 1920x1080/240"))
            .isNull()
        assertThat(ScrcpyServerManager.parseDisplayId("[server] INFO: New display: 1920x1080/240 (id=3)"))
            .isEqualTo(3)
        assertThat(ScrcpyServerManager.parseDisplayId("display_id=4"))
            .isEqualTo(4)
        assertThat(ScrcpyServerManager.parseDisplayId("Display Id=7 mDisplayId=7"))
            .isEqualTo(7)
    }

    @Test
    fun ensureServerDeployed_rejectsTamperedAssetChecksum() = runBlocking {
        val assets = mockk<AssetManager>()
        every { assets.open(ScrcpyServerManager.SERVER_ASSET_NAME) } returns
            ByteArrayInputStream("tampered-binary".toByteArray())
        val context = mockk<Context>()
        every { context.assets } returns assets
        val connection = mockk<AdbConnection>(relaxed = true)

        val manager = ScrcpyServerManager(context, connection)
        val result = manager.ensureServerDeployed()

        assertThat(result).isFalse()
        coVerify(exactly = 0) { connection.pushFile(any(), any(), any()) }
    }

    @Test
    fun ensureServerDeployed_fallsBackToVerifiedRemoteSha256() = runBlocking {
        val assets = mockk<AssetManager>()
        every { assets.open(ScrcpyServerManager.SERVER_ASSET_NAME) } throws FileNotFoundException()
        val context = mockk<Context>()
        every { context.assets } returns assets

        val connection = mockk<AdbConnection>()
        coEvery { connection.executeShell("sha256sum ${ScrcpyServerManager.SERVER_REMOTE_PATH}") } returnsMany
            listOf(
                "${ScrcpyServerManager.EXPECTED_SERVER_SHA256}  ${ScrcpyServerManager.SERVER_REMOTE_PATH}",
                "0000000000000000  ${ScrcpyServerManager.SERVER_REMOTE_PATH}"
            )

        val manager = ScrcpyServerManager(context, connection)
        assertThat(manager.ensureServerDeployed()).isTrue()
        assertThat(manager.ensureServerDeployed()).isFalse()
    }

    @Test
    fun startServer_opensLoopbackBridgeDetectsDisplayIdAndSetsFreeformMode() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        val connection = mockk<AdbConnection>(relaxed = true)
        val shellChannel = Channel<ByteArray>(Channel.UNLIMITED)
        val shellStream = mockk<AdbStream>(relaxed = true)
        every { shellStream.readChannel } returns shellChannel
        every { shellStream.close() } answers { shellChannel.close() }

        coEvery { connection.openStream(match { it.startsWith("shell:") }) } returns shellStream
        coEvery { connection.executeShell(any()) } returns ""

        val manager = ScrcpyServerManager(context, connection)
        val session = manager.startServer(DisplayProfile.PRESET_1080P)

        assertThat(session.localPort).isGreaterThan(0)
        assertThat(manager.sessionInfo.value).isEqualTo(session)

        shellChannel.trySend(
            "Device: Samsung Galaxy\n[server] INFO: New display: 1920x1080/240 (id=3)\n".toByteArray()
        )

        val updated = withTimeout(3000) {
            manager.sessionInfo.first { it?.displayId == 3 }
        }
        assertThat(updated?.localPort).isEqualTo(session.localPort)
        coVerify(timeout = 3000) {
            connection.executeShell("wm set-display-windowing-mode -d 3 5")
        }

        manager.stopServer()
        assertThat(manager.sessionInfo.value).isNull()
    }
}
