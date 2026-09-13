package com.percontext.app.feature.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.percontext.app.MainActivity
import com.percontext.app.data.settings.createAppearanceSettingsRepository
import com.percontext.app.domain.appearance.AppTheme
import com.percontext.app.domain.appearance.ThemeMode
import com.percontext.app.ui.theme.paletteFor
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class AppearanceIntegrationTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private val repository by lazy { createAppearanceSettingsRepository(context) }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        object : ExternalResource() {
            override fun before() = resetAppearance()
            override fun after() = resetAppearance()
        },
    ).around(composeRule)

    @Test
    fun appearanceSelectedInSettingsSurvivesActivityRecreation() {
        awaitMainScreen()
        composeRule.onNodeWithContentDescription("打开设置").performClick()
        composeRule.onNodeWithText("晴空奶白").performClick()
        composeRule.onNodeWithText("奶油海盐").performClick()
        composeRule.onNodeWithText("日间").performClick()
        composeRule.onNodeWithText("夜间").performClick()
        awaitSavedAppearance(AppTheme.SEA_SALT, ThemeMode.DARK)
        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.onNodeWithContentDescription("开始录音").assertIsDisplayed()
        assertBackground(AppTheme.SEA_SALT, darkTheme = true)
        composeRule.activityRule.scenario.recreate()
        awaitMainScreen()
        assertBackground(AppTheme.SEA_SALT, darkTheme = true)

        composeRule.onNodeWithContentDescription("打开设置").performClick()
        composeRule.onNodeWithText("奶油海盐").assertIsDisplayed()
        composeRule.onNodeWithText("夜间").assertIsDisplayed()
    }

    @Test
    fun everyAppearanceRendersRecordAndReviewScreensAndExportsScreenshots() {
        awaitMainScreen()
        val screenshotDirectory = File(requireNotNull(context.getExternalFilesDir(null)), "theme-qa")
        assertTrue(screenshotDirectory.isDirectory || screenshotDirectory.mkdirs())

        AppTheme.entries.forEach { theme ->
            listOf(false, true).forEach { darkTheme ->
                val mode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT
                runBlocking {
                    repository.setTheme(theme)
                    repository.setMode(mode)
                }
                awaitSavedAppearance(theme, mode)
                selectTab("记录")
                assertBackground(theme, darkTheme)
                composeRule.onNodeWithContentDescription("开始录音").assertIsDisplayed()
                composeRule.onNodeWithTag("recording_controls").assertIsDisplayed()
                composeRule.onNodeWithTag("pidan_perch").assertIsDisplayed()
                composeRule.onNodeWithText("还没有记录").assertExists()
                saveScreenshot(screenshotDirectory, theme, darkTheme, "record")

                selectTab("回顾")
                assertBackground(theme, darkTheme)
                composeRule.onNodeWithTag("selected_day_recordings").assertIsDisplayed()
                composeRule.onNodeWithText("生成回顾").assertIsDisplayed()
                composeRule.onNodeWithText("0 段录音").assertExists()
                saveScreenshot(screenshotDirectory, theme, darkTheme, "review")
            }
        }
    }

    private fun resetAppearance() {
        runBlocking {
            repository.setTheme(AppTheme.SKY)
            repository.setMode(ThemeMode.LIGHT)
        }
    }

    private fun awaitMainScreen() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("打开设置")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitSavedAppearance(theme: AppTheme, mode: ThemeMode) {
        runBlocking {
            withTimeout(10_000) {
                repository.settings.first { it.theme == theme && it.mode == mode }
            }
        }
        composeRule.waitForIdle()
    }

    private fun selectTab(label: String) {
        composeRule.onNode(
            hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
    }

    private fun assertBackground(theme: AppTheme, darkTheme: Boolean) {
        val expected = paletteFor(theme, darkTheme).background.toArgb()
        composeRule.waitUntil(timeoutMillis = 5_000) { renderedBackground() == expected }
        assertEquals("$theme dark=$darkTheme background", expected, renderedBackground())
    }

    private fun renderedBackground(): Int {
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        return pixels[1, pixels.height / 2].toArgb()
    }

    private fun saveScreenshot(directory: File, theme: AppTheme, darkTheme: Boolean, page: String) {
        val mode = if (darkTheme) "night" else "day"
        val file = File(directory, "${theme.name.lowercase(Locale.ROOT)}-$mode-$page.png")
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { output ->
            assertTrue("Could not write ${file.name}", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
