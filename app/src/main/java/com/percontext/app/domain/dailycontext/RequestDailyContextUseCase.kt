package com.percontext.app.domain.dailycontext

import com.percontext.app.domain.llm.LlmSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class RequestDailyContextUseCase(
    private val contextRepository: DailyContextRepository,
    private val sourceRepository: DailyContextSourceRepository,
    private val settingsRepository: LlmSettingsRepository,
    private val scheduler: DailyContextScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(
        dayKey: String,
        zoneId: String,
        allowChangedInput: Boolean = false,
    ): DailyContextRequestResult {
        val current = contextRepository.find(dayKey)
        if (current?.status == DailyContextStatus.QUEUED ||
            current?.status == DailyContextStatus.PROCESSING
        ) {
            return DailyContextRequestResult.ALREADY_RUNNING
        }

        val dayRange = localDayRange(dayKey, zoneId)
        val sources = sourceRepository.findBetween(dayRange.startMillis, dayRange.endExclusiveMillis)
            .sortedBy(DailyContextSource::recordedAtMillis)
        val inputChanged = current?.content != null &&
            current.inputFingerprint != null &&
            current.inputFingerprint != dailyContextInputFingerprint(sources)
        if (inputChanged && !allowChangedInput) {
            return DailyContextRequestResult.INPUT_CHANGED
        }
        if (sources.isEmpty()) {
            return DailyContextRequestResult.NO_TRANSCRIPTS
        }

        val settings = settingsRepository.current()
        if (!settings.hasApiKey || settingsRepository.apiKey(settings.providerId).isNullOrBlank()) {
            return DailyContextRequestResult.MISSING_API_KEY
        }

        contextRepository.markQueued(dayKey, zoneId, clock())
        return try {
            scheduler.enqueue(dayKey, zoneId)
            DailyContextRequestResult.QUEUED
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                contextRepository.markFailed(
                    dayKey = dayKey,
                    zoneId = zoneId,
                    failureCode = DailyContextFailureCode.ENQUEUE_FAILED,
                    updatedAtMillis = clock(),
                )
            }
            if (error is CancellationException) throw error
            DailyContextRequestResult.ENQUEUE_FAILED
        }
    }
}

enum class DailyContextRequestResult {
    QUEUED,
    INPUT_CHANGED,
    NO_TRANSCRIPTS,
    MISSING_API_KEY,
    ALREADY_RUNNING,
    ENQUEUE_FAILED,
}
