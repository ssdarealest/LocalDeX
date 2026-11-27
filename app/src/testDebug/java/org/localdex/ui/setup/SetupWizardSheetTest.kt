package org.localdex.ui.setup

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.localdex.ui.theme.LocalDeXTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h1200dp")
class SetupWizardSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun setupWizardContent_unpairedState_hidesEnhancementsAndShowsDiscoveryPort() {
        var triggeredPorts: Pair<Int, Int>? = null

        composeTestRule.setContent {
            LocalDeXTheme(darkTheme = true, dynamicColor = false) {
                SetupWizardContent(
                    hasNotificationPermission = true,
                    isDeveloperOptionsEnabled = true,
                    hasSecureSettingsPermission = false,
                    discoveredPairingPort = 37123,
                    discoveredConnectPort = 39888,
                    onRequestNotificationPermission = {},
                    onTriggerPairingNotification = { pairPort, connPort ->
                        triggeredPorts = pairPort to connPort
                    },
                    onOpenDeveloperSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Guided Setup Wizard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pairing service detected on port 37123! Tap below to open notification reply.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Show Reply Notification").performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertThat(triggeredPorts).isEqualTo(37123 to 39888)
        }
        composeTestRule.onNodeWithText("One UI Desktop Enhancements").assertDoesNotExist()
    }

    @Test
    fun setupWizardContent_withSecureSettings_showsDesktopEnhancementsAndDispatchesToggles() {
        var freeformValue: Boolean? = null
        var resizableValue: Boolean? = null
        var desktopValue: Boolean? = null

        composeTestRule.setContent {
            LocalDeXTheme(darkTheme = true, dynamicColor = false) {
                SetupWizardContent(
                    hasNotificationPermission = true,
                    isDeveloperOptionsEnabled = true,
                    hasSecureSettingsPermission = true,
                    discoveredPairingPort = null,
                    discoveredConnectPort = null,
                    isFreeformEnabled = false,
                    isForceResizableEnabled = false,
                    isDesktopModeEnabled = false,
                    onToggleFreeform = { freeformValue = it },
                    onToggleForceResizable = { resizableValue = it },
                    onToggleDesktopMode = { desktopValue = it },
                    onRequestNotificationPermission = {},
                    onTriggerPairingNotification = { _, _ -> },
                    onOpenDeveloperSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("One UI Desktop Enhancements").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Movable Freeform Windows").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Force Resizable Activities").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Force Desktop Windowing").performScrollTo().assertIsDisplayed()

        val switches = composeTestRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        )
        switches[0].performScrollTo().performClick()
        switches[1].performScrollTo().performClick()
        switches[2].performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertThat(freeformValue).isTrue()
            assertThat(resizableValue).isTrue()
            assertThat(desktopValue).isTrue()
        }
    }
}
