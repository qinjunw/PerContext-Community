package com.percontext.app.domain.dailycontext

import com.percontext.app.domain.llm.LlmConfigurationException
import com.percontext.app.domain.llm.LlmProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class GenerateDailyContextUseCase(
    private val contextRepository: DailyContextRepository,
    private val sourceRepository: DailyContextSourceRepository,
    private val llmProvider: LlmProvider,
    private val payloadBuilder: DailyContextPayloadBuilder = DailyContextPayloadBuilder(),
    private val responseParser: DailyContextResponseParser = DailyContextResponseParser(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(dayKey: String, zoneId: String) {
        val range = localDayRange(dayKey, zoneId)
        val sources = sourceRepository.findBetween(range.startMillis, range.endExclusiveMillis)
            .sortedBy(DailyContextSource::recordedAtMillis)
            .mapIndexed { index, source -> source.copy(position = index) }
        if (sources.isEmpty()) {
            contextRepository.markFailed(
                dayKey,
                zoneId,
                DailyContextFailureCode.NO_TRANSCRIPTS,
                clock(),
            )
            throw DailyContextGenerationException("No transcripts for $dayKey")
        }

        val fingerprint = dailyContextInputFingerprint(sources)
        contextRepository.markProcessing(dayKey, zoneId, fingerprint, clock())
        try {
            val response = llmProvider.generate(payloadBuilder.build(dayKey, zoneId, sources))
            val parsed = responseParser.parse(response.content)
            val now = clock()
            contextRepository.saveSuccessful(
                context = DailyContext(
                    dayKey = dayKey,
                    zoneId = zoneId,
                    content = parsed.content,
                    structuredJson = parsed.structuredJson,
                    providerId = response.providerId,
                    model = response.model,
                    status = DailyContextStatus.SUCCEEDED,
                    inputFingerprint = fingerprint,
                    sourceCount = sources.size,
                    generatedAtMillis = now,
                    updatedAtMillis = now,
                ),
                sources = sources,
            )
        } catch (cancellation: CancellationException) {
            try {
                withContext(NonCancellable) {
                    contextRepository.markFailed(
                        dayKey = dayKey,
                        zoneId = zoneId,
                        failureCode = DailyContextFailureCode.CANCELLED,
                        updatedAtMillis = clock(),
                    )
                }
            } catch (cleanupFailure: Throwable) {
                if (cleanupFailure !== cancellation) {
                    cancellation.addSuppressed(cleanupFailure)
                }
            }
            throw cancellation
        } catch (error: Throwable) {
            contextRepository.markFailed(
                dayKey = dayKey,
                zoneId = zoneId,
                failureCode = when (error) {
                    is InvalidDailyContextResponseException -> DailyContextFailureCode.INVALID_RESPONSE
                    is LlmConfigurationException -> DailyContextFailureCode.MISSING_API_KEY
                    else -> DailyContextFailureCode.PROVIDER_ERROR
                },
                updatedAtMillis = clock(),
            )
            throw error
        }
    }
}

class DailyContextGenerationException(message: String) : IllegalStateException(message)
