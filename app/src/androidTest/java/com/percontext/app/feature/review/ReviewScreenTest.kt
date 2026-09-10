package com.percontext.app.feature.review

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.percontext.app.domain.dailycontext.DailyContext
import com.percontext.app.domain.dailycontext.DailyContextContent
import com.percontext.app.domain.dailycontext.DailyContextStatus
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.ui.theme.PerContextTheme
import java.util.Calendar
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedDayOpensReadOnlyRecordingStatusesWithoutMakingPageScrollable() {
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

        composeRule.onNodeWithTag("review_page").assert(!hasScrollAction())
        composeRule.onNodeWithTag("selected_day_recordings").performClick()

        composeRule.onNodeWithTag("day_recordings_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("已转写").assertIsDisplayed()
        composeRule.onNodeWithText("未转写").assertIsDisplayed()
        composeRule.onNodeWithTag("day_recording_record_1").assert(!hasClickAction())

        composeRule.onNodeWithContentDescription("关闭当天录音").performClick()
        composeRule.onNodeWithTag("day_recordings_dialog").assertDoesNotExist()
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
        composeRule.onNodeWithText("第一段。\n\n第二段。").assertIsDisplayed()

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
