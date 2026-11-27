package org.localdex.core.adb

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.localdex.core.adb.connection.AdbConnection
import org.localdex.core.adb.permission.AdbPermissionManager

class AdbPermissionManagerTest {

    private lateinit var mockConnection: AdbConnection
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockConnection = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
    }

    @Test
    fun grantSecureSettings_executesPmGrantCommand() = runBlocking {
        coEvery { mockConnection.executeShell(any()) } returns "Success\n"

        val result = AdbPermissionManager.grantSecureSettings(mockConnection, "org.localdex")
        assertThat(result.isSuccess).isTrue()

        coVerify {
            mockConnection.executeShell("pm grant org.localdex android.permission.WRITE_SECURE_SETTINGS")
        }
    }

    @Test
    fun setFreeformSupport_executesExpectedAdbCommands() = runBlocking {
        coEvery { mockConnection.executeShell(any()) } returns ""

        val success = AdbPermissionManager.setFreeformSupport(mockContext, mockConnection, true)
        assertThat(success).isTrue()

        coVerify {
            mockConnection.executeShell("settings put global enable_freeform_support 1")
        }
    }

    @Test
    fun setForceResizableActivities_executesExpectedAdbCommands() = runBlocking {
        coEvery { mockConnection.executeShell(any()) } returns ""

        val success = AdbPermissionManager.setForceResizableActivities(mockContext, mockConnection, true)
        assertThat(success).isTrue()

        coVerify {
            mockConnection.executeShell("settings put global force_resizable_activities 1")
        }
    }

    @Test
    fun setDesktopModeDebug_configuresSetpropAndGlobalFlag() = runBlocking {
        coEvery { mockConnection.executeShell(any()) } returns ""

        val success = AdbPermissionManager.setDesktopModeDebug(mockConnection, true)
        assertThat(success).isTrue()

        coVerify {
            mockConnection.executeShell("setprop persist.wm.debug.desktop_mode 1")
            mockConnection.executeShell("settings put global force_desktop_mode_on_external_displays 1")
        }
    }
}
