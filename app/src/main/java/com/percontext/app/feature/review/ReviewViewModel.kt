package com.percontext.app.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.percontext.app.domain.dailycontext.DailyContext
import com.percontext.app.domain.dailycontext.DailyContextRequestResult
import com.percontext.app.domain.dailycontext.dayKeyAt
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReviewCalendarDay(
    val cell: CalendarDayCell,
    val recordingCount: Int,
    val context: DailyContext?,
)

data class ReviewUiState(
    val month: CalendarMonth,
    val days: List<ReviewCalendarDay>,
    val selectedDayKey: String,
    val selectedTranscriptCount: Int,
    val selectedRecordings: List<ReviewRecordingSummary>,
    val selectedContext: DailyContext?,
    val todayDayKey: String,
    val zoneId: String,
)

data class ReviewTimeSnapshot(
    val epochMillis: Long,
    val zoneId: String,
)

fun interface ReviewTimeProvider {
    fun current(): ReviewTimeSnapshot
}

private object SystemReviewTimeProvider : ReviewTimeProvider {
    override fun current(): ReviewTimeSnapshot = ReviewTimeSnapshot(
        epochMillis = System.currentTimeMillis(),
        zoneId = TimeZone.getDefault().id,
    )
}

private data class ReviewCalendarState(
    val month: CalendarMonth,
    val selectedDayKey: String,
    val todayDayKey: String,
    val zoneId: String,
)

class ReviewViewModel(
    private val gateway: ReviewFeatureGateway,
    private val timeProvider: ReviewTimeProvider = SystemReviewTimeProvider,
) : ViewModel() {
    private val initialTime = timeProvider.current()
    private val initialTodayDayKey = initialTime.dayKey()
    private val calendarState = MutableStateFlow(
        ReviewCalendarState(
            month = CalendarMonth.at(initialTime.epochMillis, initialTime.zoneId),
            selectedDayKey = initialTodayDayKey,
            todayDayKey = initialTodayDayKey,
            zoneId = initialTime.zoneId,
        ),
    )
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val mutableRegenerationConfirmationDayKey = MutableStateFlow<String?>(null)

    val messages: Flow<String> = mutableMessages
    val regenerationConfirmationDayKey: StateFlow<String?> =
        mutableRegenerationConfirmationDayKey.asStateFlow()
    val uiState: StateFlow<ReviewUiState> = combine(
        gateway.dailyContexts,
        gateway.transcribedRecords,
        gateway.recordingSummaries,
        calendarState,
    ) { contexts, transcribedRecords, recordings, calendar ->
        val contextsByDay = contexts.associateBy(DailyContext::dayKey)
        val transcriptCounts = transcribedRecords
            .groupingBy { dayKeyAt(it.recordedAtMillis, calendar.zoneId) }
            .eachCount()
        val recordingsByDay = recordings.groupBy {
            dayKeyAt(it.recordedAtMillis, calendar.zoneId)
        }
        val days = calendar.month.cells().map { cell ->
            val dayKey = cell.dayKey
            ReviewCalendarDay(
                cell = cell,
                recordingCount = dayKey?.let { recordingsByDay[it]?.size }.orZero(),
                context = dayKey?.let(contextsByDay::get),
            )
        }
        ReviewUiState(
            month = calendar.month,
            days = days,
            selectedDayKey = calendar.selectedDayKey,
            selectedTranscriptCount = transcriptCounts[calendar.selectedDayKey].orZero(),
            selectedRecordings = recordingsByDay[calendar.selectedDayKey]
                .orEmpty()
                .sortedByDescending(ReviewRecordingSummary::recordedAtMillis),
            selectedContext = contextsByDay[calendar.selectedDayKey],
            todayDayKey = calendar.todayDayKey,
            zoneId = calendar.zoneId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ReviewUiState(
            month = calendarState.value.month,
            days = calendarState.value.month.cells().map { ReviewCalendarDay(it, 0, null) },
            selectedDayKey = calendarState.value.selectedDayKey,
            selectedTranscriptCount = 0,
            selectedRecordings = emptyList(),
            selectedContext = null,
            todayDayKey = calendarState.value.todayDayKey,
            zoneId = calendarState.value.zoneId,
        ),
    )

    fun onScreenResumed() {
        refreshCurrentTime(alignVisibleDay = true)
    }

    fun previousMonth() = changeMonth(-1)

    fun nextMonth() = changeMonth(1)

    fun selectDay(dayKey: String) {
        calendarState.value = calendarState.value.copy(selectedDayKey = dayKey)
        mutableRegenerationConfirmationDayKey.value = null
    }

    fun requestDailyContext() {
        val current = calendarState.value
        enqueueDailyContext(
            dayKey = current.selectedDayKey,
            zoneId = current.zoneId,
            allowChangedInput = false,
        )
    }

    fun confirmRegeneration() {
        val dayKey = mutableRegenerationConfirmationDayKey.value ?: return
        mutableRegenerationConfirmationDayKey.value = null
        enqueueDailyContext(
            dayKey = dayKey,
            zoneId = calendarState.value.zoneId,
            allowChangedInput = true,
        )
    }

    fun dismissRegenerationConfirmation() {
        mutableRegenerationConfirmationDayKey.value = null
    }

    private fun enqueueDailyContext(
        dayKey: String,
        zoneId: String,
        allowChangedInput: Boolean,
    ) {
        viewModelScope.launch {
            try {
                when (gateway.requestDailyContext(dayKey, zoneId, allowChangedInput)) {
                    DailyContextRequestResult.QUEUED -> Unit
                    DailyContextRequestResult.INPUT_CHANGED ->
                        mutableRegenerationConfirmationDayKey.value = dayKey
                    DailyContextRequestResult.NO_TRANSCRIPTS ->
                        mutableMessages.emit("这一天还没有可整理的转写")
                    DailyContextRequestResult.MISSING_API_KEY ->
                        mutableMessages.emit("请先在设置中保存模型服务 API Key")
                    DailyContextRequestResult.ALREADY_RUNNING ->
                        mutableMessages.emit("这一天正在生成回顾")
                    DailyContextRequestResult.ENQUEUE_FAILED ->
                        mutableMessages.emit("未能启动回顾任务，请稍后重试")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                mutableMessages.emit("未能启动回顾任务，请稍后重试")
            }
        }
    }

    private fun changeMonth(delta: Int) {
        val currentTime = refreshCurrentTime(alignVisibleDay = false)
        val current = calendarState.value
        val changed = current.month.shifted(delta)
        val currentMonth = CalendarMonth.at(currentTime.epochMillis, currentTime.zoneId)
        calendarState.value = current.copy(
            month = changed,
            selectedDayKey = if (changed == currentMonth) {
                currentTime.dayKey()
            } else {
                changed.firstDayKey()
            },
        )
        mutableRegenerationConfirmationDayKey.value = null
    }

    private fun refreshCurrentTime(alignVisibleDay: Boolean): ReviewTimeSnapshot {
        val currentTime = timeProvider.current()
        val current = calendarState.value
        val currentTodayDayKey = currentTime.dayKey()
        val changed = current.todayDayKey != currentTodayDayKey ||
            current.zoneId != currentTime.zoneId
        if (changed) {
            calendarState.value = current.copy(
                month = if (alignVisibleDay) {
                    CalendarMonth.at(currentTime.epochMillis, currentTime.zoneId)
                } else {
                    current.month
                },
                selectedDayKey = if (alignVisibleDay) currentTodayDayKey else current.selectedDayKey,
                todayDayKey = currentTodayDayKey,
                zoneId = currentTime.zoneId,
            )
            mutableRegenerationConfirmationDayKey.value = null
        }
        return currentTime
    }
}

private fun ReviewTimeSnapshot.dayKey(): String = dayKeyAt(epochMillis, zoneId)

private fun Int?.orZero(): Int = this ?: 0
