package com.percontext.app.feature.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.percontext.app.domain.appearance.AppTheme
import com.percontext.app.domain.dailycontext.DailyContext
import com.percontext.app.domain.dailycontext.DailyContextContent
import com.percontext.app.domain.dailycontext.DailyContextStatus
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.ui.theme.PerContextTheme
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedDayOpensReadOnlyRecordingStatuses() {
        val recordings = listOf(
            ReviewRecordingSummary(
                recordId = "record_2",
                recordedAtMillis = 1_777_777_002_000L,
                durationMillis = 120_000L,
                transcriptStatus = TranscriptStatus.NOT_REQUESTED,
            ),
            ReviewRecordingSummary(
                recordId = "record_1",
                recordedAtMillis = 1_777_777_001_000L,
                durationMillis = 60_000L,
                transcriptStatus = TranscriptStatus.SUCCEEDED,
            ),
        )
        composeRule.setContent {
            PerContextTheme {
                ReviewScreen(
                    state = reviewState(recordings),
                    snackbarHostState = SnackbarHostState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onSelectDay = {},
                    onRequestDailyContext = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("selected_day_recordings").performScrollTo().performClick()

        composeRule.onNodeWithTag("day_recordings_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("已转写").assertIsDisplayed()
        composeRule.onNodeWithText("未转写").assertIsDisplayed()
        composeRule.onNodeWithTag("day_recording_record_1").assert(!hasClickAction())

        composeRule.onNodeWithContentDescription("关闭当天录音").performClick()
        composeRule.onNodeWithTag("day_recordings_dialog").assertDoesNotExist()
    }

    @Test
    fun allThemesKeepSelectedDatesReadableAndThePerchLeavesDateActionsAvailable() {
        val theme = mutableStateOf(AppTheme.SKY)
        val dark = mutableStateOf(false)
        var selectedDay: String? = null
        composeRule.setContent {
            PerContextTheme(theme = theme.value, darkTheme = dark.value) {
                ReviewScreen(
                    state = reviewState(emptyList()),
                    snackbarHostState = SnackbarHostState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onSelectDay = { selectedDay = it },
                    onRequestDailyContext = {},
                    onOpenSettings = {},
                )
            }
        }

        AppTheme.entries.forEach { appTheme ->
            listOf(false, true).forEach { darkTheme ->
                composeRule.runOnIdle {
                    theme.value = appTheme
                    dark.value = darkTheme
                }
                val selectedDate = composeRule.onNodeWithTag("review_day_2026-08-27")
                    .performScrollTo()
                    .assertIsSelected()
                val textLayouts = mutableListOf<TextLayoutResult>()
                composeRule.onNodeWithText("27", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                        it(textLayouts)
                    }
                val textColor = textLayouts.single().layoutInput.style.color
                val pixels = selectedDate.captureToImage().toPixelMap()
                val colorCounts = mutableMapOf<Int, Int>()
                for (y in 0 until pixels.height) {
                    for (x in 0 until pixels.width) {
                        val color = pixels[x, y].toArgb()
                        colorCounts[color] = colorCounts.getOrDefault(color, 0) + 1
                    }
                }
                val background = Color(colorCounts.maxBy { it.value }.key)
                val contrast = (max(textColor.luminance(), background.luminance()) + 0.05f) /
                    (min(textColor.luminance(), background.luminance()) + 0.05f)
                assertTrue("$appTheme dark=$darkTheme selected date contrast=$contrast", contrast >= 4.5f)

                composeRule.onNodeWithTag("review_day_2026-08-28").performClick()
                composeRule.runOnIdle { assertEquals("2026-08-28", selectedDay) }
                composeRule.onNodeWithTag("pidan_perch").performScrollTo().assertIsDisplayed()
                composeRule.onNodeWithTag("selected_day_recordings").performScrollTo().performClick()
                composeRule.onNodeWithTag("day_recordings_dialog").assertIsDisplayed()
                composeRule.onNodeWithContentDescription("关闭当天录音").performClick()
            }
        }
    }

    @Test
    fun compactHeightAndLargeTextKeepGenerateReviewReachable() {
        var generated = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.6f)) {
                PerContextTheme(theme = AppTheme.SEA_SALT, darkTheme = true) {
                    Box(Modifier.width(360.dp).height(480.dp)) {
                        ReviewScreen(
                            state = reviewState(emptyList()).copy(selectedTranscriptCount = 1),
                            snackbarHostState = SnackbarHostState(),
                            onPreviousMonth = {},
                            onNextMonth = {},
                            onSelectDay = {},
                            onRequestDailyContext = { generated = true },
                            onOpenSettings = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("生成回顾").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(generated) }
        composeRule.onNodeWithTag("selected_day_recordings").performScrollTo().performClick()
        composeRule.onNodeWithTag("day_recordings_dialog").assertIsDisplayed()
    }

    @Test
    fun selectedDayUsesTheCompactReviewActionWithoutExplanatoryCopy() {
        composeRule.setContent {
            PerContextTheme {
                ReviewScreen(
                    state = reviewState(
                        listOf(
                            ReviewRecordingSummary(
                                recordId = "record_1",
                                recordedAtMillis = 1_777_777_001_000L,
                                durationMillis = 60_000L,
                                transcriptStatus = TranscriptStatus.SUCCEEDED,
                            ),
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onSelectDay = {},
                    onRequestDailyContext = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("生成回顾").assertIsDisplayed()
        composeRule.onNodeWithText("把当天转写整理成一份可回看的结构化摘要。")
            .assertDoesNotExist()
        composeRule.onNodeWithText("将当天转写发送至你配置的模型服务")
            .assertDoesNotExist()
    }

    @Test
    fun successfulContextRemainsAfterRecordingsAreDeletedAndOpensScrollableOverlay() {
        composeRule.setContent {
            PerContextTheme {
                ReviewScreen(
                    state = reviewState(emptyList()).copy(selectedContext = successfulContext()),
                    snackbarHostState = SnackbarHostState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onSelectDay = {},
                    onRequestDailyContext = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("review_context_preview").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("daily_context_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_context_dialog_content").assert(hasScrollAction())
        composeRule.onNode(
            hasText("第一段。\n\n第二段。") and
                hasAnyAncestor(hasTestTag("daily_context_dialog_content")),
        ).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("关闭当天回顾").performClick()
        composeRule.onNodeWithTag("daily_context_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("review_context_preview").assertIsDisplayed()
    }

    @Test
    fun changedContextInputRequiresBackupConfirmationBeforeRegeneration() {
        var confirmed = false
        composeRule.setContent {
            PerContextTheme {
                ReviewScreen(
                    state = reviewState(emptyList()).copy(selectedContext = successfulContext()),
                    snackbarHostState = SnackbarHostState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onSelectDay = {},
                    onRequestDailyContext = {},
                    showRegenerationConfirmation = true,
                    onConfirmRegeneration = { confirmed = true },
                    onDismissRegenerationConfirmation = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("重新生成回顾？").assertIsDisplayed()
        composeRule.onNodeWithText("确认并重新生成").performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }
    }

    private fun reviewState(recordings: List<ReviewRecordingSummary>): ReviewUiState {
        val month = CalendarMonth(2026, Calendar.AUGUST)
        return ReviewUiState(
            month = month,
            days = month.cells().map { cell ->
                ReviewCalendarDay(
                    cell = cell,
                    recordingCount = if (cell.day == 27) recordings.size else 0,
                    context = null,
                )
            },
            selectedDayKey = "2026-08-27",
            selectedTranscriptCount = recordings.count {
                it.transcriptStatus == TranscriptStatus.SUCCEEDED
            },
            selectedRecordings = recordings,
            selectedContext = null,
            todayDayKey = "2026-08-28",
            zoneId = "Asia/Hong_Kong",
        )
    }

    private fun successfulContext() = DailyContext(
        dayKey = "2026-08-27",
        zoneId = "Asia/Hong_Kong",
        content = DailyContextContent(
            title = "删除前的回顾",
            summary = "第一段。\n\n第二段。",
            topics = List(30) { index -> "主题 ${index + 1}" },
            ideas = emptyList(),
            questions = emptyList(),
            decisions = emptyList(),
            todos = emptyList(),
        ),
        structuredJson = "{}",
        providerId = "deepseek",
        model = "deepseek-v4-flash",
        status = DailyContextStatus.SUCCEEDED,
        inputFingerprint = "fingerprint",
        sourceCount = 0,
        generatedAtMillis = 1L,
        updatedAtMillis = 1L,
    )
}
