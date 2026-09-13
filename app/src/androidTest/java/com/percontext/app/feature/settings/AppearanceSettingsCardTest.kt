package com.percontext.app.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.percontext.app.domain.appearance.AppTheme
import com.percontext.app.domain.appearance.AppearanceSettings
import com.percontext.app.domain.appearance.ThemeMode
import com.percontext.app.ui.theme.PerContextTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppearanceSettingsCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeAndModeSelectionsApplyIndependentlyWithRadioSemantics() {
        var appearance by mutableStateOf(AppearanceSettings())
        composeRule.setContent {
            PerContextTheme {
                AppearanceSettingsCard(
                    appearance = appearance,
                    onSelectTheme = { appearance = appearance.copy(theme = it) },
                    onSelectMode = { appearance = appearance.copy(mode = it) },
                )
            }
        }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

        composeRule.onNodeWithText("晴空奶白").performClick()
        composeRule.onNode(hasText("晴空奶白") and radio).assertIsSelected()
        composeRule.onNode(hasText("雾紫蓝") and radio)
            .assertIsNotSelected()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(AppearanceSettings(AppTheme.LAVENDER), appearance) }

        composeRule.onNodeWithText("跟随系统").performClick()
        composeRule.onNode(hasText("跟随系统") and radio).assertIsSelected()
        composeRule.onNode(hasText("夜间") and radio)
            .assertIsNotSelected()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle {
            assertEquals(AppearanceSettings(AppTheme.LAVENDER, ThemeMode.DARK), appearance)
        }
    }
}
