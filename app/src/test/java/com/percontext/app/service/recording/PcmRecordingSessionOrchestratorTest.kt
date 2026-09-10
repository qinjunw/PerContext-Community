package com.percontext.app.service.recording

import com.percontext.app.data.file.PendingAudioRecording
import com.percontext.app.domain.model.VoiceRecord
import com.percontext.app.domain.recording.RecordingEvent
import com.percontext.app.domain.recording.RecordingSession
import com.percontext.app.domain.repository.VoiceRecordRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileDescriptor
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PcmRecordingSessionOrchestratorTest {
    @Test
    fun `engine failure settles active session without stop request`() = runTest {
        val engine = FakePcmRecordingEngine()
        val target = FakePendingRecording()
        var tickerActive = true
        val outcomes = mutableListOf<PcmRecordingSessionOutcome>()
        val orchestrator = PcmRecordingSessionOrchestrator(
            engine = engine,
            target = target,
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            onSettling = { tickerActive = false },
            persistStoppedRecording = { _, _ -> CompletedRecordingWriteResult.Stored },
            onOutcome = outcomes::add,
        )
        orchestrator.observeTerminalResult()

        engine.fail(IllegalStateException("capture failed"))
        advanceUntilIdle()

        assertFalse(tickerActive)
        assertEquals(0, engine.stopCount)
        assertEquals(1, engine.releaseCount)
        assertEquals(0, target.publishCount)
        assertEquals(1, target.discardCount)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes.single() is PcmRecordingSessionOutcome.Failed)
    }

    @Test
    fun `user stop and engine failure settle pending target once`() = runTest {
        val engine = FakePcmRecordingEngine(
            stopFailure = IllegalStateException("capture failed while stopping"),
        )
        val target = FakePendingRecording()
        var settlingCount = 0
        val outcomes = mutableListOf<PcmRecordingSessionOutcome>()
        val orchestrator = PcmRecordingSessionOrchestrator(
            engine = engine,
            target = target,
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            onSettling = { settlingCount += 1 },
            persistStoppedRecording = { _, _ -> CompletedRecordingWriteResult.Stored },
            onOutcome = outcomes::add,
        )
        orchestrator.observeTerminalResult()

        assertTrue(orchestrator.requestStop(stoppedAtMillis = 2_000L))
        engine.fail(IllegalStateException("capture failed"))
        advanceUntilIdle()

        assertEquals(1, settlingCount)
        assertEquals(1, engine.stopCount)
        assertEquals(1, engine.releaseCount)
        assertEquals(0, target.publishCount)
        assertEquals(1, target.discardCount)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes.single() is PcmRecordingSessionOutcome.Failed)
    }

    @Test
    fun `engine failure prevents a later stop from settling target again`() = runTest {
        val engine = FakePcmRecordingEngine()
        val target = FakePendingRecording()
        var settlingCount = 0
        val outcomes = mutableListOf<PcmRecordingSessionOutcome>()
        val orchestrator = PcmRecordingSessionOrchestrator(
            engine = engine,
            target = target,
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            onSettling = { settlingCount += 1 },
            persistStoppedRecording = { _, _ -> CompletedRecordingWriteResult.Stored },
            onOutcome = outcomes::add,
        )
        orchestrator.observeTerminalResult()

        engine.fail(IllegalStateException("capture failed"))
        advanceUntilIdle()

        assertFalse(orchestrator.requestStop(stoppedAtMillis = 2_000L))
        assertEquals(1, settlingCount)
        assertEquals(0, engine.stopCount)
        assertEquals(1, engine.releaseCount)
        assertEquals(1, target.discardCount)
        assertEquals(1, outcomes.size)
    }

    @Test
    fun `successful user stop returns target for existing persistence path`() = runTest {
        val engine = FakePcmRecordingEngine()
        val target = FakePendingRecording()
        val outcomes = mutableListOf<PcmRecordingSessionOutcome>()
        val orchestrator = PcmRecordingSessionOrchestrator(
            engine = engine,
            target = target,
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            onSettling = {},
            persistStoppedRecording = { _, _ -> CompletedRecordingWriteResult.Stored },
            onOutcome = outcomes::add,
        )
        orchestrator.observeTerminalResult()

        assertTrue(orchestrator.requestStop(stoppedAtMillis = 2_000L))
        advanceUntilIdle()

        assertEquals(1, engine.stopCount)
        assertEquals(0, engine.releaseCount)
        assertEquals(0, target.publishCount)
        assertEquals(0, target.discardCount)
        assertEquals(
            PcmRecordingSessionOutcome.Stopped(target, stoppedAtMillis = 2_000L),
            outcomes.single(),
        )
    }

    @Test
    fun `successful stop persists on IO before submitting short outcome on service dispatcher`() = runBlocking {
        val mainDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "recording-service-main")
        }.asCoroutineDispatcher()
        val ioDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "recording-settlement-io")
        }.asCoroutineDispatcher()
        val settlementScope = CoroutineScope(SupervisorJob() + mainDispatcher)
        val calls = ConcurrentLinkedQueue<String>()
        val outcome = CompletableDeferred<PcmRecordingSessionOutcome>()
        val engine = FakePcmRecordingEngine()
        val target = FakePendingRecording(
            onPublish = {
                calls += "publish@${Thread.currentThread().name.substringBefore(" @")}"
            },
        )
        val repository = DispatcherRecordingRepository(calls)
        try {
            val orchestrator = PcmRecordingSessionOrchestrator(
                engine = engine,
                target = target,
                scope = settlementScope,
                ioDispatcher = ioDispatcher,
                onSettling = {},
                persistStoppedRecording = { pending, _ ->
                    CompletedRecordingWriter(repository, ioDispatcher).store(
                        target = pending,
                        startedAtMillis = 1_000L,
                        stoppedAtMillis = 2_000L,
                    )
                },
                onOutcome = { settled ->
                    calls += "outcome@${Thread.currentThread().name.substringBefore(" @")}"
                    outcome.complete(settled)
                },
            )
            withContext(mainDispatcher) {
                orchestrator.observeTerminalResult()
                assertTrue(orchestrator.requestStop(stoppedAtMillis = 2_000L))
            }

            assertTrue(withTimeout(5_000L) { outcome.await() } is PcmRecordingSessionOutcome.Stopped)
            assertEquals(
                listOf(
                    "publish@recording-settlement-io",
                    "room@recording-settlement-io",
                    "outcome@recording-service-main",
                ),
                calls.toList(),
            )
            assertEquals(1, target.publishCount)
            assertEquals(1, repository.added.size)
        } finally {
            settlementScope.cancel()
            mainDispatcher.close()
            ioDispatcher.close()
        }
    }

    @Test
    fun `Room failure after publish reaches repository failed outcome`() = runTest {
        val outcomes = mutableListOf<PcmRecordingSessionOutcome>()
        val orchestrator = PcmRecordingSessionOrchestrator(
            engine = FakePcmRecordingEngine(),
            target = FakePendingRecording(),
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            onSettling = {},
            persistStoppedRecording = { _, _ -> CompletedRecordingWriteResult.RepositoryFailed },
            onOutcome = outcomes::add,
        )
        orchestrator.observeTerminalResult()

        assertTrue(orchestrator.requestStop(stoppedAtMillis = 2_000L))
        advanceUntilIdle()

        assertEquals(listOf(PcmRecordingSessionOutcome.RepositoryFailed), outcomes)
    }

    @Test
    fun `publish failure keeps writer cleanup exact once and reports publish failure`() = runTest {
        val target = FakePendingRecording()
        val outcomes = mutableListOf<PcmRecordingSessionOutcome>()
        val orchestrator = PcmRecordingSessionOrchestrator(
            engine = FakePcmRecordingEngine(),
            target = target,
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            onSettling = {},
            persistStoppedRecording = { pending, _ ->
                pending.discard()
                CompletedRecordingWriteResult.PublishFailed
            },
            onOutcome = outcomes::add,
        )
        orchestrator.observeTerminalResult()

        assertTrue(orchestrator.requestStop(stoppedAtMillis = 2_000L))
        advanceUntilIdle()

        assertEquals(1, target.discardCount)
        assertEquals(listOf(PcmRecordingSessionOutcome.PublishFailed), outcomes)
    }

    @Test
    fun `permanently blocking native shutdown reaches one failed outcome after logical timeout`() = runTest {
        val engine = PermanentlyBlockingNativeEngine()
        val target = FakePendingRecording()
        val outcomes = mutableListOf<PcmRecordingSessionOutcome>()
        var settlingCount = 0
        val orchestrator = PcmRecordingSessionOrchestrator(
            engine = engine,
            target = target,
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            onSettling = { settlingCount += 1 },
            persistStoppedRecording = { _, _ -> CompletedRecordingWriteResult.Stored },
            onOutcome = outcomes::add,
        )
        orchestrator.observeTerminalResult()

        assertTrue(orchestrator.requestStop(stoppedAtMillis = 2_000L))
        assertFalse(orchestrator.requestStop(stoppedAtMillis = 2_000L))
        advanceUntilIdle()

        assertTrue(engine.stopEntered.await(1, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(engine.releaseEntered.await(1, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(1, settlingCount)
        assertEquals(1, engine.stopCount)
        assertEquals(1, engine.releaseCount)
        assertEquals(1, engine.nativeStopCount.get())
        assertEquals(1, engine.nativeReleaseCount.get())
        assertEquals(1, target.discardCount)
        assertEquals(listOf(PcmRecordingSessionOutcome.Failed), outcomes)
    }

    @Test
    fun `scope cancellation during successful stop cannot cancel settlement`() = runTest {
        val stopEntered = CompletableDeferred<Unit>()
        val allowStop = CompletableDeferred<Unit>()
        val engine = FakePcmRecordingEngine(
            stopEntered = stopEntered,
            allowStop = allowStop,
        )
        val target = FakePendingRecording()
        val outcomes = mutableListOf<PcmRecordingSessionOutcome>()
        val sessionStore = recordingSessionStore()
        val settlementScope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        val orchestrator = PcmRecordingSessionOrchestrator(
            engine = engine,
            target = target,
            scope = settlementScope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            onSettling = {},
            persistStoppedRecording = { pending, _ ->
                pending.publish()
                CompletedRecordingWriteResult.Stored
            },
            onOutcome = { outcome ->
                if (outcome is PcmRecordingSessionOutcome.Stopped) {
                    sessionStore.dispatch(RecordingEvent.Stored)
                }
                outcomes += outcome
            },
        )
        orchestrator.observeTerminalResult()

        assertTrue(orchestrator.requestStop(stoppedAtMillis = 2_000L))
        sessionStore.dispatch(RecordingEvent.StopRequested)
        stopEntered.await()
        settlementScope.cancel()
        allowStop.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, engine.stopCount)
        assertEquals(0, engine.releaseCount)
        assertEquals(1, target.publishCount)
        assertEquals(0, target.discardCount)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes.single() is PcmRecordingSessionOutcome.Stopped)
        assertEquals(RecordingSession.Idle, sessionStore.session.value)
    }

    @Test
    fun `scope cancellation during failed stop cannot cancel cleanup`() = runTest {
        val stopEntered = CompletableDeferred<Unit>()
        val allowStop = CompletableDeferred<Unit>()
        val engine = FakePcmRecordingEngine(
            stopFailure = IllegalStateException("capture failed while stopping"),
            stopEntered = stopEntered,
            allowStop = allowStop,
        )
        val target = FakePendingRecording()
        val outcomes = mutableListOf<PcmRecordingSessionOutcome>()
        val sessionStore = recordingSessionStore()
        val settlementScope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        val orchestrator = PcmRecordingSessionOrchestrator(
            engine = engine,
            target = target,
            scope = settlementScope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            onSettling = {},
            persistStoppedRecording = { _, _ -> CompletedRecordingWriteResult.Stored },
            onOutcome = { outcome ->
                outcomes += outcome
                if (outcome is PcmRecordingSessionOutcome.Failed) {
                    sessionStore.dispatch(RecordingEvent.Failed("capture failed"))
                }
            },
        )
        orchestrator.observeTerminalResult()

        assertTrue(orchestrator.requestStop(stoppedAtMillis = 2_000L))
        sessionStore.dispatch(RecordingEvent.StopRequested)
        stopEntered.await()
        settlementScope.cancel()
        allowStop.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, engine.stopCount)
        assertEquals(1, engine.releaseCount)
        assertEquals(0, target.publishCount)
        assertEquals(1, target.discardCount)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes.single() is PcmRecordingSessionOutcome.Failed)
        assertTrue(sessionStore.session.value is RecordingSession.Failed)
    }

    @Test
    fun `precompleted terminal clears activated session without stale writeback`() {
        val registry = PcmRecordingSessionRegistry()
        lateinit var session: FakePcmRecordingSession
        session = FakePcmRecordingSession(
            onObserve = { registry.clear(session) },
        )

        registry.activate(session)

        assertEquals(1, session.observeCount)
        assertNull(registry.active)
    }

    @Test
    fun `destroy settles active recording once and publishes interrupted audio`() = runTest {
        val engine = FakePcmRecordingEngine()
        val target = FakePendingRecording()
        val outcomes = mutableListOf<PcmRecordingSessionOutcome>()
        val orchestrator = PcmRecordingSessionOrchestrator(
            engine = engine,
            target = target,
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            onSettling = {},
            persistStoppedRecording = { _, _ -> CompletedRecordingWriteResult.Stored },
            onOutcome = outcomes::add,
        )
        orchestrator.observeTerminalResult()

        assertTrue(orchestrator.requestDestroy())
        assertFalse(orchestrator.requestDestroy())
        advanceUntilIdle()

        assertEquals(1, engine.stopCount)
        assertEquals(0, engine.releaseCount)
        assertEquals(1, target.publishCount)
        assertEquals(0, target.discardCount)
        assertEquals(listOf(PcmRecordingSessionOutcome.Interrupted), outcomes)
    }
}

private fun recordingSessionStore(): RecordingSessionStore = RecordingSessionStore().apply {
    dispatch(RecordingEvent.StartRequested)
    dispatch(RecordingEvent.Started(startedAtMillis = 1_000L))
}

private class FakePcmRecordingSession(
    private val onObserve: () -> Unit,
) : PcmRecordingSession {
    var observeCount = 0

    override fun observeTerminalResult() {
        observeCount += 1
        onObserve()
    }

    override fun requestStop(stoppedAtMillis: Long): Boolean = false

    override fun requestDestroy(): Boolean = false
}

private class FakePcmRecordingEngine(
    private val stopFailure: Throwable? = null,
    private val stopEntered: CompletableDeferred<Unit>? = null,
    private val allowStop: CompletableDeferred<Unit>? = null,
) : PcmRecordingEngine {
    private val terminal = CompletableDeferred<PcmRecordingTerminalResult>()
    override val terminalResult = terminal
    var stopCount = 0
    var releaseCount = 0

    override fun start() = Unit

    override suspend fun stop() {
        stopCount += 1
        stopEntered?.complete(Unit)
        allowStop?.await()
        stopFailure?.let { throw it }
    }

    override fun release() {
        releaseCount += 1
    }

    fun fail(error: Throwable) {
        terminal.complete(PcmRecordingTerminalResult.Failed(error))
    }
}

private class PermanentlyBlockingNativeEngine : PcmRecordingEngine {
    private val terminal = CompletableDeferred<PcmRecordingTerminalResult>()
    override val terminalResult = terminal
    val stopEntered = CountDownLatch(1)
    val releaseEntered = CountDownLatch(1)
    val nativeStopCount = AtomicInteger()
    val nativeReleaseCount = AtomicInteger()
    private val shutdown = PcmInputShutdownRequester(
        stopInput = permanentlyBlocking(nativeStopCount, stopEntered),
        releaseInput = permanentlyBlocking(nativeReleaseCount, releaseEntered),
    )
    var stopCount = 0
    var releaseCount = 0

    override fun start() = Unit

    override suspend fun stop() {
        stopCount += 1
        PcmCaptureStopCoordinator(
            requestStop = shutdown::requestStop,
            awaitCompletion = { false },
            forceRelease = shutdown::requestRelease,
            relinquishOwnership = {},
            gracefulTimeoutMillis = 0L,
            forcedTimeoutMillis = 0L,
        ).stop()
    }

    override fun release() {
        releaseCount += 1
        shutdown.requestStop()
        shutdown.requestRelease()
    }

    private fun permanentlyBlocking(
        count: AtomicInteger,
        entered: CountDownLatch,
    ): () -> Unit = {
        count.incrementAndGet()
        entered.countDown()
        while (true) {
            try {
                CountDownLatch(1).await()
            } catch (_: InterruptedException) {
                // Simulate a native call that never returns and ignores interruption.
            }
        }
    }
}

private class FakePendingRecording(
    private val onPublish: () -> Unit = {},
) : PendingAudioRecording {
    override val recordId = "record_test"
    override val audioLocation = "content://record_test"
    override val fileDescriptor = FileDescriptor()
    var publishCount = 0
    var discardCount = 0

    override fun publish() {
        publishCount += 1
        onPublish()
    }

    override fun discard() {
        discardCount += 1
    }
}

private class DispatcherRecordingRepository(
    private val calls: MutableCollection<String>,
) : VoiceRecordRepository {
    override val records: Flow<List<VoiceRecord>> = MutableStateFlow(emptyList())
    val added = mutableListOf<VoiceRecord>()

    override suspend fun add(record: VoiceRecord) {
        calls += "room@${Thread.currentThread().name.substringBefore(" @")}"
        added += record
    }

    override suspend fun findById(id: String): VoiceRecord? = added.firstOrNull { it.id == id }

    override suspend fun delete(id: String): Boolean = false
}
