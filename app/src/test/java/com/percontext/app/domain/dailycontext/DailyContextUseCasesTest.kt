package com.percontext.app.domain.dailycontext

import com.percontext.app.domain.llm.LlmProvider
import com.percontext.app.domain.llm.LlmResponse
import com.percontext.app.domain.llm.LlmSettings
import com.percontext.app.domain.llm.LlmSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyContextUseCasesTest {
    @Test
    fun `request requires transcripts and a locally stored key before enqueue`() = runTest {
        val contextRepository = FakeDailyContextRepository()
        val sourceRepository = FakeSourceRepository(emptyList())
        val settingsRepository = FakeSettingsRepository(hasKey = false)
        var enqueued = 0
        val useCase = RequestDailyContextUseCase(
            contextRepository,
            sourceRepository,
            settingsRepository,
            scheduler = DailyContextScheduler { _, _ -> enqueued++ },
            clock = { 10L },
        )

        assertEquals(
            DailyContextRequestResult.NO_TRANSCRIPTS,
            useCase("2026-08-27", "Asia/Hong_Kong"),
        )

        sourceRepository.sources = listOf(source())
        assertEquals(
            DailyContextRequestResult.MISSING_API_KEY,
            useCase("2026-08-27", "Asia/Hong_Kong"),
        )
        assertEquals(0, enqueued)
        assertNull(contextRepository.value)
    }

    @Test
    fun `request marks queued before scheduling once`() = runTest {
        val contextRepository = FakeDailyContextRepository()
        val sourceRepository = FakeSourceRepository(listOf(source()))
        val settingsRepository = FakeSettingsRepository(hasKey = true)
        var enqueued = 0
        val useCase = RequestDailyContextUseCase(
            contextRepository,
            sourceRepository,
            settingsRepository,
            scheduler = DailyContextScheduler { _, _ -> enqueued++ },
            clock = { 10L },
        )

        assertEquals(
            DailyContextRequestResult.QUEUED,
            useCase("2026-08-27", "Asia/Hong_Kong"),
        )
        assertEquals(
            DailyContextRequestResult.ALREADY_RUNNING,
            useCase("2026-08-27", "Asia/Hong_Kong"),
        )
        assertEquals(1, enqueued)
        assertEquals(DailyContextStatus.QUEUED, contextRepository.value?.status)
    }

    @Test
    fun `changed sources require confirmation before replacing a successful context`() = runTest {
        val previousSources = listOf(source(recordId = "record_old"))
        val contextRepository = FakeDailyContextRepository(
            initial = dailyContext(
                DailyContextContent(
                    title = "旧回顾",
                    summary = "继续保留",
                    topics = emptyList(),
                    ideas = emptyList(),
                    questions = emptyList(),
                    decisions = emptyList(),
                    todos = emptyList(),
                ),
            ).copy(inputFingerprint = dailyContextInputFingerprint(previousSources)),
        )
        val sourceRepository = FakeSourceRepository(listOf(source(recordId = "record_new")))
        var enqueued = 0
        val useCase = RequestDailyContextUseCase(
            contextRepository,
            sourceRepository,
            FakeSettingsRepository(hasKey = true),
            scheduler = DailyContextScheduler { _, _ -> enqueued++ },
        )

        assertEquals(
            DailyContextRequestResult.INPUT_CHANGED,
            useCase("2026-08-27", "Asia/Hong_Kong"),
        )
        assertEquals(DailyContextStatus.SUCCEEDED, contextRepository.value?.status)
        assertEquals(0, enqueued)

        assertEquals(
            DailyContextRequestResult.QUEUED,
            useCase("2026-08-27", "Asia/Hong_Kong", allowChangedInput = true),
        )
        assertEquals(1, enqueued)
    }

    @Test
    fun `deleted sources keep the successful context before and after confirmation`() = runTest {
        val previousSources = listOf(source(recordId = "record_deleted"))
        val previous = dailyContext(
            DailyContextContent(
                title = "删除前的回顾",
                summary = "不能消失",
                topics = emptyList(),
                ideas = emptyList(),
                questions = emptyList(),
                decisions = emptyList(),
                todos = emptyList(),
            ),
        ).copy(inputFingerprint = dailyContextInputFingerprint(previousSources))
        val contextRepository = FakeDailyContextRepository(initial = previous)
        val useCase = RequestDailyContextUseCase(
            contextRepository,
            FakeSourceRepository(emptyList()),
            FakeSettingsRepository(hasKey = true),
            scheduler = DailyContextScheduler { _, _ -> error("must not enqueue") },
        )

        assertEquals(
            DailyContextRequestResult.INPUT_CHANGED,
            useCase("2026-08-27", "Asia/Hong_Kong"),
        )
        assertEquals(
            DailyContextRequestResult.NO_TRANSCRIPTS,
            useCase("2026-08-27", "Asia/Hong_Kong", allowChangedInput = true),
        )
        assertEquals(previous.content, contextRepository.value?.content)
        assertEquals(DailyContextStatus.SUCCEEDED, contextRepository.value?.status)
    }

    @Test
    fun `generation saves validated content and ordered sources`() = runTest {
        val contextRepository = FakeDailyContextRepository()
        val later = source(recordId = "later", time = 20L)
        val earlier = source(recordId = "earlier", time = 10L)
        val sourceRepository = FakeSourceRepository(listOf(later, earlier))
        val provider = LlmProvider {
            LlmResponse(
                content = COMPLETE_JSON,
                providerId = "deepseek",
                model = "deepseek-v4-flash",
            )
        }
        val useCase = GenerateDailyContextUseCase(
            contextRepository,
            sourceRepository,
            provider,
            clock = { 100L },
        )

        useCase("2026-08-27", "Asia/Hong_Kong")

        assertEquals(DailyContextStatus.SUCCEEDED, contextRepository.value?.status)
        assertEquals("当天回顾", contextRepository.value?.content?.title)
        assertEquals(listOf("earlier", "later"), contextRepository.savedSources.map { it.recordId })
        assertEquals(listOf(0, 1), contextRepository.savedSources.map { it.position })
        assertEquals("deepseek-v4-flash", contextRepository.value?.model)
    }

    @Test
    fun `invalid model response marks failure and keeps previous content`() = runTest {
        val previousContent = DailyContextContent(
            title = "旧回顾",
            summary = "保留",
            topics = emptyList(),
            ideas = emptyList(),
            questions = emptyList(),
            decisions = emptyList(),
            todos = emptyList(),
        )
        val contextRepository = FakeDailyContextRepository(
            initial = dailyContext(previousContent),
        )
        val failure = runCatching {
            GenerateDailyContextUseCase(
                contextRepository,
                FakeSourceRepository(listOf(source())),
                LlmProvider { LlmResponse("{}", "deepseek", "deepseek-v4-flash") },
                clock = { 100L },
            )("2026-08-27", "Asia/Hong_Kong")
        }.exceptionOrNull()

        assertTrue(failure is InvalidDailyContextResponseException)
        assertEquals(DailyContextStatus.FAILED, contextRepository.value?.status)
        assertEquals(DailyContextFailureCode.INVALID_RESPONSE, contextRepository.value?.failureCode)
        assertEquals(previousContent, contextRepository.value?.content)
    }

    @Test
    fun `cancelled generation keeps the previous context and can be requested again`() = runTest {
        val sources = listOf(source())
        val previousContent = DailyContextContent(
            title = "旧回顾",
            summary = "继续保留",
            topics = listOf("原主题"),
            ideas = emptyList(),
            questions = emptyList(),
            decisions = emptyList(),
            todos = emptyList(),
        )
        val previousFingerprint = dailyContextInputFingerprint(sources)
        val contextRepository = FakeDailyContextRepository(
            initial = dailyContext(previousContent).copy(inputFingerprint = previousFingerprint),
        )
        val sourceRepository = FakeSourceRepository(sources)
        val providerStarted = CompletableDeferred<Unit>()
        val observedFailure = CompletableDeferred<Throwable>()
        val generation = launch {
            try {
                GenerateDailyContextUseCase(
                    contextRepository,
                    sourceRepository,
                    LlmProvider {
                        providerStarted.complete(Unit)
                        awaitCancellation()
                    },
                    clock = { 100L },
                )("2026-08-27", "Asia/Hong_Kong")
            } catch (error: Throwable) {
                observedFailure.complete(error)
            }
        }
        providerStarted.await()
        generation.cancel(CancellationException("cancelled"))
        generation.join()
        val cancellation = observedFailure.await()

        assertTrue(cancellation is CancellationException)
        assertEquals("cancelled", cancellation.message)
        assertTrue(generation.isCancelled)
        assertEquals(DailyContextStatus.FAILED, contextRepository.value?.status)
        assertEquals(DailyContextFailureCode.CANCELLED, contextRepository.value?.failureCode)
        assertEquals(previousContent, contextRepository.value?.content)
        assertEquals(previousFingerprint, contextRepository.value?.inputFingerprint)

        var enqueued = 0
        val requestResult = RequestDailyContextUseCase(
            contextRepository,
            sourceRepository,
            FakeSettingsRepository(hasKey = true),
            scheduler = DailyContextScheduler { _, _ -> enqueued++ },
        )("2026-08-27", "Asia/Hong_Kong")

        assertEquals(DailyContextRequestResult.QUEUED, requestResult)
        assertEquals(1, enqueued)
    }

    @Test
    fun `state cleanup failure does not replace generation cancellation`() = runTest {
        val cleanupFailure = IllegalStateException("cannot persist cancellation")
        val contextRepository = FakeDailyContextRepository(markFailedError = cleanupFailure)
        val providerStarted = CompletableDeferred<Unit>()
        val observedFailure = CompletableDeferred<Throwable>()
        val generation = launch {
            try {
                GenerateDailyContextUseCase(
                    contextRepository,
                    FakeSourceRepository(listOf(source())),
                    LlmProvider {
                        providerStarted.complete(Unit)
                        awaitCancellation()
                    },
                )("2026-08-27", "Asia/Hong_Kong")
            } catch (error: Throwable) {
                observedFailure.complete(error)
            }
        }
        providerStarted.await()
        generation.cancel(CancellationException("cancelled"))
        generation.join()
        val cancellation = observedFailure.await()

        assertTrue(cancellation is CancellationException)
        assertEquals("cancelled", cancellation.message)
        val suppressed = cancellation.suppressed.single()
        assertTrue(suppressed is IllegalStateException)
        assertEquals(cleanupFailure.message, suppressed.message)
    }

    private companion object {
        const val COMPLETE_JSON =
            """{"title":"当天回顾","summary":"完成测试","topics":[],"ideas":[],"questions":[],"decisions":[],"todos":[]}"""

        fun source(recordId: String = "record_1", time: Long = 1_787_760_000_000L) =
            DailyContextSource(recordId, "transcript_$recordId", time, "正文")

        fun dailyContext(content: DailyContextContent) = DailyContext(
            dayKey = "2026-08-27",
            zoneId = "Asia/Hong_Kong",
            content = content,
            structuredJson = COMPLETE_JSON,
            providerId = "deepseek",
            model = "deepseek-v4-flash",
            status = DailyContextStatus.SUCCEEDED,
            inputFingerprint = "old",
            sourceCount = 1,
            generatedAtMillis = 1L,
            updatedAtMillis = 1L,
        )
    }
}

private class FakeDailyContextRepository(
    initial: DailyContext? = null,
    private val markFailedError: Throwable? = null,
) : DailyContextRepository {
    private val state = MutableStateFlow(initial?.let(::listOf).orEmpty())
    var value: DailyContext?
        get() = state.value.singleOrNull()
        private set(value) {
            state.value = value?.let(::listOf).orEmpty()
        }
    var savedSources: List<DailyContextSource> = emptyList()

    override val contexts: Flow<List<DailyContext>> = state

    override suspend fun find(dayKey: String): DailyContext? = value?.takeIf { it.dayKey == dayKey }

    override suspend fun markQueued(dayKey: String, zoneId: String, updatedAtMillis: Long) {
        value = placeholder(dayKey, zoneId, DailyContextStatus.QUEUED, updatedAtMillis)
    }

    override suspend fun markProcessing(
        dayKey: String,
        zoneId: String,
        inputFingerprint: String,
        updatedAtMillis: Long,
    ) {
        value = placeholder(dayKey, zoneId, DailyContextStatus.PROCESSING, updatedAtMillis)
            .copy(inputFingerprint = inputFingerprint)
    }

    override suspend fun saveSuccessful(context: DailyContext, sources: List<DailyContextSource>) {
        value = context
        savedSources = sources
    }

    override suspend fun markFailed(
        dayKey: String,
        zoneId: String,
        failureCode: DailyContextFailureCode,
        updatedAtMillis: Long,
    ) {
        currentCoroutineContext().ensureActive()
        markFailedError?.let { throw it }
        value = placeholder(dayKey, zoneId, DailyContextStatus.FAILED, updatedAtMillis)
            .copy(failureCode = failureCode)
    }

    private fun placeholder(
        dayKey: String,
        zoneId: String,
        status: DailyContextStatus,
        updatedAtMillis: Long,
    ): DailyContext = value?.copy(
        status = status,
        updatedAtMillis = updatedAtMillis,
        failureCode = null,
    ) ?: DailyContext(
        dayKey = dayKey,
        zoneId = zoneId,
        content = null,
        structuredJson = null,
        providerId = null,
        model = null,
        status = status,
        inputFingerprint = null,
        sourceCount = 0,
        generatedAtMillis = null,
        updatedAtMillis = updatedAtMillis,
    )
}

private class FakeSourceRepository(
    var sources: List<DailyContextSource>,
) : DailyContextSourceRepository {
    override val transcribedRecords: Flow<List<TranscribedRecordStamp>> = MutableStateFlow(
        sources.map { TranscribedRecordStamp(it.recordId, it.recordedAtMillis) },
    )

    override suspend fun findBetween(
        startMillis: Long,
        endExclusiveMillis: Long,
    ): List<DailyContextSource> = sources
}

private class FakeSettingsRepository(
    hasKey: Boolean,
) : LlmSettingsRepository {
    private val state = MutableStateFlow(LlmSettings(hasApiKey = hasKey))
    private var key = if (hasKey) "test-key" else null
    override val settings: Flow<LlmSettings> = state

    override suspend fun current(): LlmSettings = state.value

    override suspend fun save(providerId: String, apiKey: String?, baseUrl: String, model: String) {
        key = apiKey ?: key
        state.value = LlmSettings(providerId, key != null)
    }

    override suspend fun apiKey(providerId: String): String? = key

    override suspend fun clearApiKey(providerId: String) {
        key = null
        state.value = LlmSettings(providerId, hasApiKey = false)
    }
}
