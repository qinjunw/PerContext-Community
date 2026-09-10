package com.percontext.app.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.percontext.app.ui.theme.PerContextTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppHeaderGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsActionDoesNotMoveWhenTitleContentChangesHeight() {
        var showSubtitle by mutableStateOf(true)
        composeRule.setContent {
            PerContextTheme {
                AppHeaderGrid(
                    titleContent = {
                        Text("PerContext")
                        if (showSubtitle) Text("把想法留在当下")
                    },
                    actionContent = {
                        HeaderSettingsButton(onClick = {})
                    },
                )
            }
        }

        val twoLineBounds = composeRule.onNodeWithTag("app_header_settings")
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.runOnIdle { showSubtitle = false }

        val oneLineBounds = composeRule.onNodeWithTag("app_header_settings")
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(twoLineBounds, oneLineBounds)
    }
}
