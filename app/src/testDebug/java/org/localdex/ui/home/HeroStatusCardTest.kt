package org.localdex.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.localdex.core.scrcpy.model.DisplayProfile
import org.localdex.core.scrcpy.server.ScrcpySessionInfo
import org.localdex.service.SessionState
import org.localdex.ui.theme.LocalDeXTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeroStatusCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun heroStatusCard_errorState_displaysMessageAndSetupRetryAction() {
        var setupClicked = false

        composeTestRule.setContent {
            LocalDeXTheme(darkTheme = true, dynamicColor = false) {
                HeroStatusCard(
                    sessionState = SessionState.Error("Checksum verification failed"),
                    isPaired = true,
                    hasSecureSettings = true,
                    onOpenSetup = { setupClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Session Error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Checksum verification failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap to complete Setup Wizard").performClick()
        assertThat(setupClicked).isTrue()
    }

    @Test
    fun heroStatusCard_runningState_displaysActiveDisplayId() {
        val info = ScrcpySessionInfo(displayId = 4, localPort = 39100, profile = DisplayProfile.PRESET_1080P)

        composeTestRule.setContent {
            LocalDeXTheme(darkTheme = true, dynamicColor = false) {
                HeroStatusCard(
                    sessionState = SessionState.Running(info),
                    isPaired = true,
                    hasSecureSettings = true,
                    onOpenSetup = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Session Active (Display #4)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Samsung DeX is running on virtual display").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap to complete Setup Wizard").assertDoesNotExist()
    }

    @Test
    fun heroStatusCard_unpairedAndReadyStates_renderAccuratePrompts() {
        composeTestRule.setContent {
            LocalDeXTheme(darkTheme = true, dynamicColor = false) {
                HeroStatusCard(
                    sessionState = SessionState.Idle,
                    isPaired = false,
                    hasSecureSettings = false,
                    onOpenSetup = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Pairing Required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap to complete Setup Wizard").assertIsDisplayed()
    }

    @Test
    fun heroStatusCard_readyState_hidesSetupPrompt() {
        composeTestRule.setContent {
            LocalDeXTheme(darkTheme = true, dynamicColor = false) {
                HeroStatusCard(
                    sessionState = SessionState.Idle,
                    isPaired = true,
                    hasSecureSettings = true,
                    onOpenSetup = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Ready to Launch").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap to complete Setup Wizard").assertDoesNotExist()
    }
}
