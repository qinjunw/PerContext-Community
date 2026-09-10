package com.percontext.app.feature.record

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.percontext.app.domain.model.RecordStatus
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.model.VoiceRecord
import com.percontext.app.domain.recording.RecordingSession
import com.percontext.app.ui.theme.PerContextTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyScreenKeepsRecordActionProminent() {
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("开始录音").assertIsDisplayed()
        composeRule.onNodeWithText("最近记录").assertIsDisplayed()
        composeRule.onNodeWithText("还没有记录").assertIsDisplayed()
        composeRule.onNodeWithText("本地保存").assertDoesNotExist()
    }

    @Test
    fun recordingTransitionsDoNotShowLongTransientStatusText() {
        var screenState by mutableStateOf(
            RecordUiState(recordingSession = RecordingSession.Starting),
        )
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = screenState,
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("准备记录").assertIsDisplayed()
        composeRule.onNodeWithText("正在启动麦克风…").assertDoesNotExist()

        composeRule.runOnIdle {
            screenState = RecordUiState(recordingSession = RecordingSession.Stopping)
        }

        composeRule.onNodeWithText("准备记录").assertIsDisplayed()
        composeRule.onNodeWithText("正在保存到本地…").assertDoesNotExist()
    }

    @Test
    fun recordingStateShowsRippleIndicator() {
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(
                        recordingSession = RecordingSession.Recording(
                            startedAtMillis = System.currentTimeMillis(),
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("recording_ripple").assertIsDisplayed()
    }

    @Test
    fun deleteActionDelegatesToConfirmationOwner() {
        var deleteRequested = false
        val record = VoiceRecord(
            id = "record_1",
            createdAtMillis = 10L,
            durationMillis = 1_000L,
            audioLocation = "/recordings/record_1.m4a",
            audioCodec = "AAC-LC/M4A",
            status = RecordStatus.RECORDED,
        )
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(records = listOf(record)),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = { deleteRequested = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("删除录音").performClick()

        composeRule.runOnIdle { assertTrue(deleteRequested) }
    }

    @Test
    fun onlyRecentRecordsOwnVerticalScrolling() {
        val records = (1..8).map { index ->
            VoiceRecord(
                id = "record_$index",
                createdAtMillis = index.toLong(),
                durationMillis = 1_000L,
                audioLocation = "/recordings/record_$index.m4a",
                audioCodec = "AAC-LC/M4A",
                status = RecordStatus.RECORDED,
            )
        }
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(records = records),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("recording_controls").assertIsDisplayed()
        composeRule.onNodeWithTag("recording_controls").assert(!hasScrollAction())
        composeRule.onNodeWithTag("recent_records_list").assert(hasScrollAction())
        composeRule.onNodeWithTag("recent_records_list").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("recording_controls").assertIsDisplayed()
    }

    @Test
    fun newRecordReturnsRecentRecordsToTheTop() {
        val records = (1..12).map(::record)
        var screenState by mutableStateOf(RecordUiState(records = records))
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = screenState,
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("recent_records_list").performScrollToNode(
            hasTestTag("recording_card_record_12"),
        )

        composeRule.runOnIdle {
            screenState = RecordUiState(records = listOf(record(99)) + records)
        }

        composeRule.onNodeWithTag("recording_card_record_99").assertIsDisplayed()
    }

    @Test
    fun recordCardShowsDurationAndFileSize() {
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(
                        records = listOf(record(1).copy(fileSizeBytes = 1_536L)),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("原始录音 · 00:01 · 1.5 KB").assertIsDisplayed()
    }

    @Test
    fun transcribeActionDelegatesSelectedRecord() {
        val record = record(1)
        var requestedRecord: VoiceRecord? = null
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(records = listOf(record)),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = { requestedRecord = it },
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("播放").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("转写录音").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("删除录音").assertIsDisplayed()

        composeRule.runOnIdle { assertTrue(requestedRecord == record) }
    }

    @Test
    fun successfulTranscriptOpensFromItsCardAndClosesBackToTimeline() {
        var transcriptionRequested = false
        val transcript = "今天下午三点讨论本地语音转文字。"
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(
                        records = listOf(
                            record(1).copy(
                                transcriptStatus = TranscriptStatus.SUCCEEDED,
                                transcriptText = transcript,
                            ),
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = { transcriptionRequested = true },
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithText(transcript).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("转写已完成")
            .assertIsDisplayed()
            .assertIsNotEnabled()

        composeRule.onNodeWithTag("recording_card_record_1").performClick()

        composeRule.onNodeWithText("转写内容").assertIsDisplayed()
        composeRule.onNodeWithText(transcript).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭转写内容").performClick()
        composeRule.onNodeWithText(transcript).assertDoesNotExist()
        composeRule.onNodeWithTag("recording_card_record_1").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(!transcriptionRequested) }
    }

    @Test
    fun successfulTranscriptClosesWhenBackdropIsTapped() {
        val transcript = "点击浮层外区域后关闭。"
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(
                        records = listOf(
                            record(1).copy(
                                transcriptStatus = TranscriptStatus.SUCCEEDED,
                                transcriptText = transcript,
                            ),
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("recording_card_record_1").performClick()
        composeRule.onNodeWithText(transcript).assertIsDisplayed()

        composeRule.onNodeWithTag("transcript_backdrop").performTouchInput {
            click(Offset(1f, 1f))
        }

        composeRule.onNodeWithText(transcript).assertDoesNotExist()
        composeRule.onNodeWithTag("recording_card_record_1").assertIsDisplayed()
    }

    @Test
    fun longTranscriptScrollsInsideItsOverlay() {
        val longTranscript = List(80) { index -> "第${index + 1}段转写内容。" }.joinToString("\n")
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(
                        records = listOf(
                            record(1).copy(
                                transcriptStatus = TranscriptStatus.SUCCEEDED,
                                transcriptText = longTranscript,
                            ),
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("recording_card_record_1").performClick()

        composeRule.onNodeWithTag("transcript_content").assert(hasScrollAction())
        composeRule.onNodeWithTag("transcript_dialog").assertIsDisplayed()
    }

    @Test
    fun playbackButtonDoesNotOpenSuccessfulTranscript() {
        var playbackRequested = false
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(
                        records = listOf(
                            record(1).copy(
                                transcriptStatus = TranscriptStatus.SUCCEEDED,
                                transcriptText = "已完成的转写。",
                            ),
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = { playbackRequested = true },
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("播放").performClick()

        composeRule.onNodeWithText("转写内容").assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(playbackRequested) }
    }

    @Test
    fun processingTranscriptUsesTheRecordTranscribeButtonAsItsOnlyStatus() {
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(
                        records = listOf(
                            record(1).copy(transcriptStatus = TranscriptStatus.PROCESSING),
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("转写中").assertIsDisplayed()
    }

    @Test
    fun failedTranscriptCanBeRetriedWithoutHidingRecordingMetadata() {
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(
                        records = listOf(
                            record(1).copy(transcriptStatus = TranscriptStatus.FAILED),
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("转写失败，录音已保留").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("转写录音").assertIsDisplayed()
        composeRule.onNodeWithText("原始录音 · 00:01 · --").assertIsDisplayed()
    }

    @Test
    fun corruptedRecoveryIsExplicitlyLabeled() {
        composeRule.setContent {
            PerContextTheme {
                RecordScreen(
                    state = RecordUiState(
                        records = listOf(record(1).copy(status = RecordStatus.CORRUPTED)),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> },
                    onRequestTranscription = {},
                    onRequestDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("文件可能损坏 · 00:01 · --").assertIsDisplayed()
    }

    private fun record(index: Int) = VoiceRecord(
        id = "record_$index",
        createdAtMillis = index.toLong(),
        durationMillis = 1_000L,
        audioLocation = "/recordings/record_$index.m4a",
        audioCodec = "AAC-LC/M4A",
        status = RecordStatus.RECORDED,
    )
}
