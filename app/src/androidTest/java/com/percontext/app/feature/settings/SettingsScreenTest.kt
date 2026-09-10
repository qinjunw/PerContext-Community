package com.percontext.app.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.percontext.app.ui.theme.PerContextTheme
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutCardOpensBundledSenseVoiceLicenseAndAttribution() {
        composeRule.setContent {
            PerContextTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onSelectProvider = {},
                    onApiKeyChange = {},
                    onToggleApiKeyVisibility = {},
                    onClearApiKey = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("开源许可"))
        composeRule.onNodeWithText("开源许可").performClick()

        composeRule.onNodeWithText("SenseVoiceSmall 开源许可").assertIsDisplayed()
        composeRule.onNodeWithText("FunAudioLLM and Alibaba Group", substring = true)
            .assertExists()
        composeRule.onNodeWithText("Version: 1.1", substring = true).assertExists()
    }
}
