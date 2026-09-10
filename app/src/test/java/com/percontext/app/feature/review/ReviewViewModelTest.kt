package com.percontext.app.feature.review

import com.percontext.app.domain.dailycontext.DailyContext
import com.percontext.app.domain.dailycontext.DailyContextRequestResult
import com.percontext.app.domain.dailycontext.TranscribedRecordStamp
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.feature.record.MainDispatcherRule
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `calendar projects every recording and its transcript state into the selected local day`() = runTest {
        val instant = hongKongInstant(2026, Calendar.AUGUST, 27)
        val gateway = FakeReviewGateway(
            stamps = listOf(TranscribedRecordStamp("record_1", instant)),
            recordings = listOf(
                ReviewRecordingSummary(
                    recordId = "record_1",
                    recordedAtMillis = instant,
                    durationMillis = 60_000L,
                    transcriptStatus = TranscriptStatus.SUCCEEDED,
                ),
                ReviewRecordingSummary(
                    recordId = "record_2",
                    recordedAtMillis = instant + 1_000L,
                    durationMillis = 30_000L,
                    transcriptStatus = TranscriptStatus.NOT_REQUESTED,
                ),
            ),
        )
        val viewModel = ReviewViewModel(
            gateway = gateway,
            timeProvider = MutableReviewTimeProvider(
                ReviewTimeSnapshot(instant, "Asia/Hong_Kong"),
            ),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        assertEquals("2026-08-27", viewModel.uiState.value.selectedDayKey)
        assertEquals(1, viewModel.uiState.value.selectedTranscriptCount)
        assertEquals(2, viewModel.uiState.value.days.single { it.cell.day == 27 }.recordingCount)
        assertEquals(
            listOf("record_2", "record_1"),
            viewModel.uiState.value.selectedRecordings.map(ReviewRecordingSummary::recordId),
        )
        assertEquals(
            TranscriptStatus.NOT_REQUESTED,
            viewModel.uiState.value.selectedRecordings.first().transcriptStatus,
        )
    }

    @Test
    fun `manual generation delegates the selected day and device zone`() = runTest {
        val instant = hongKongInstant(2026, Calendar.AUGUST, 27)
        val gateway = FakeReviewGateway(emptyList())
        val viewModel = ReviewViewModel(
            gateway,
            MutableReviewTimeProvider(ReviewTimeSnapshot(instant, "Asia/Hong_Kong")),
        )

        viewModel.selectDay("2026-08-12")
        viewModel.requestDailyContext()

        assertEquals(
            listOf(Triple("2026-08-12", "Asia/Hong_Kong", false)),
            gateway.requests,
        )
    }

    @Test
    fun `changed input asks for confirmation before retrying with override`() = runTest {
        val instant = hongKongInstant(2026, Calendar.AUGUST, 27)
        val gateway = FakeReviewGateway(
            stamps = emptyList(),
            requestResults = ArrayDeque(
                listOf(
                    DailyContextRequestResult.INPUT_CHANGED,
                    DailyContextRequestResult.QUEUED,
                ),
            ),
        )
        val viewModel = ReviewViewModel(
            gateway,
            MutableReviewTimeProvider(ReviewTimeSnapshot(instant, "Asia/Hong_Kong")),
        )

        viewModel.requestDailyContext()

        assertEquals("2026-08-27", viewModel.regenerationConfirmationDayKey.value)
        assertEquals(
            listOf(Triple("2026-08-27", "Asia/Hong_Kong", false)),
            gateway.requests,
        )

        viewModel.confirmRegeneration()

        assertEquals(null, viewModel.regenerationConfirmationDayKey.value)
        assertEquals(
            listOf(
                Triple("2026-08-27", "Asia/Hong_Kong", false),
                Triple("2026-08-27", "Asia/Hong_Kong", true),
            ),
            gateway.requests,
        )
    }

    @Test
    fun `resume after month end aligns visible month selected day and today marker`() = runTest {
        val timeProvider = MutableReviewTimeProvider(
            reviewTime(2026, Calendar.JANUARY, 31, 23, 59, "Asia/Hong_Kong"),
        )
        val viewModel = ReviewViewModel(FakeReviewGateway(emptyList()), timeProvider)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        timeProvider.current = reviewTime(
            2026,
            Calendar.FEBRUARY,
            1,
            0,
            1,
            "Asia/Hong_Kong",
        )
        viewModel.onScreenResumed()

        assertEquals(CalendarMonth(2026, Calendar.FEBRUARY), viewModel.uiState.value.month)
        assertEquals("2026-02-01", viewModel.uiState.value.selectedDayKey)
        assertEquals("2026-02-01", viewModel.uiState.value.todayDayKey)
    }

    @Test
    fun `resume on the same local day and zone preserves a manually selected day`() = runTest {
        val timeProvider = MutableReviewTimeProvider(
            reviewTime(2026, Calendar.AUGUST, 27, 8, 0, "Asia/Hong_Kong"),
        )
        val viewModel = ReviewViewModel(FakeReviewGateway(emptyList()), timeProvider)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        viewModel.selectDay("2026-08-12")

        timeProvider.current = reviewTime(
            2026,
            Calendar.AUGUST,
            27,
            22,
            0,
            "Asia/Hong_Kong",
        )
        viewModel.onScreenResumed()

        assertEquals("2026-08-12", viewModel.uiState.value.selectedDayKey)
        assertEquals(CalendarMonth(2026, Calendar.AUGUST), viewModel.uiState.value.month)
    }

    @Test
    fun `month navigation refreshes today without skipping the next visible month`() = runTest {
        val timeProvider = MutableReviewTimeProvider(
            reviewTime(2026, Calendar.JANUARY, 31, 23, 59, "Asia/Hong_Kong"),
        )
        val viewModel = ReviewViewModel(FakeReviewGateway(emptyList()), timeProvider)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        timeProvider.current = reviewTime(
            2026,
            Calendar.FEBRUARY,
            1,
            0,
            1,
            "Asia/Hong_Kong",
        )
        viewModel.nextMonth()

        assertEquals(CalendarMonth(2026, Calendar.FEBRUARY), viewModel.uiState.value.month)
        assertEquals("2026-02-01", viewModel.uiState.value.selectedDayKey)
        assertEquals("2026-02-01", viewModel.uiState.value.todayDayKey)
    }

    @Test
    fun `timezone change regroups recordings into the displayed local day`() = runTest {
        val recordedAt = reviewTime(
            2026,
            Calendar.AUGUST,
            31,
            23,
            30,
            "UTC",
        ).epochMillis
        val timeProvider = MutableReviewTimeProvider(
            ReviewTimeSnapshot(recordedAt, "UTC"),
        )
        val gateway = FakeReviewGateway(
            stamps = listOf(TranscribedRecordStamp("record_1", recordedAt)),
            recordings = listOf(
                ReviewRecordingSummary(
                    recordId = "record_1",
                    recordedAtMillis = recordedAt,
                    durationMillis = 30_000L,
                    transcriptStatus = TranscriptStatus.SUCCEEDED,
                ),
            ),
        )
        val viewModel = ReviewViewModel(gateway, timeProvider)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        assertEquals("2026-08-31", viewModel.uiState.value.selectedDayKey)

        timeProvider.current = ReviewTimeSnapshot(recordedAt, "Asia/Tokyo")
        viewModel.onScreenResumed()

        assertEquals("2026-09-01", viewModel.uiState.value.selectedDayKey)
        assertEquals("Asia/Tokyo", viewModel.uiState.value.zoneId)
        assertEquals(1, viewModel.uiState.value.selectedTranscriptCount)
        assertEquals(
            listOf("record_1"),
            viewModel.uiState.value.selectedRecordings.map(ReviewRecordingSummary::recordId),
        )
    }

    private fun hongKongInstant(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Hong_Kong")).apply {
            clear()
            set(year, month, day, 12, 0)
        }.timeInMillis
}

private class MutableReviewTimeProvider(
    var current: ReviewTimeSnapshot,
) : ReviewTimeProvider {
    override fun current(): ReviewTimeSnapshot = current
}

private fun reviewTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    zoneId: String,
): ReviewTimeSnapshot = ReviewTimeSnapshot(
    epochMillis = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply {
        clear()
        set(year, month, day, hour, minute)
    }.timeInMillis,
    zoneId = zoneId,
)

private class FakeReviewGateway(
    stamps: List<TranscribedRecordStamp>,
    recordings: List<ReviewRecordingSummary> = emptyList(),
    private val requestResults: ArrayDeque<DailyContextRequestResult> = ArrayDeque(),
) : ReviewFeatureGateway {
    override val dailyContexts: Flow<List<DailyContext>> = MutableStateFlow(emptyList())
    override val transcribedRecords: Flow<List<TranscribedRecordStamp>> = MutableStateFlow(stamps)
    override val recordingSummaries: Flow<List<ReviewRecordingSummary>> = MutableStateFlow(recordings)
    val requests = mutableListOf<Triple<String, String, Boolean>>()

    override suspend fun requestDailyContext(
        dayKey: String,
        zoneId: String,
        allowChangedInput: Boolean,
    ): DailyContextRequestResult {
        requests += Triple(dayKey, zoneId, allowChangedInput)
        return requestResults.removeFirstOrNull() ?: DailyContextRequestResult.QUEUED
    }
}
