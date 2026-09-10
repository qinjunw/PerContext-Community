package com.percontext.app.service.recording

import com.percontext.app.data.file.PendingAudioRecording
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

internal interface PcmRecordingEngine {
    val terminalResult: Deferred<PcmRecordingTerminalResult>

    fun start()

    suspend fun stop()

    fun release()
}

internal sealed interface PcmRecordingTerminalResult {
    data object Completed : PcmRecordingTerminalResult

    data class Failed(val error: Throwable) : PcmRecordingTerminalResult
}

internal sealed interface PcmRecordingSessionOutcome {
    data class Stopped(
        val target: PendingAudioRecording,
        val stoppedAtMillis: Long,
    ) : PcmRecordingSessionOutcome

    data object Failed : PcmRecordingSessionOutcome

    data object PublishFailed : PcmRecordingSessionOutcome

    data object RepositoryFailed : PcmRecordingSessionOutcome

    data object Interrupted : PcmRecordingSessionOutcome
}

internal interface PcmRecordingSession {
    fun observeTerminalResult()

    fun requestStop(stoppedAtMillis: Long): Boolean

    fun requestDestroy(): Boolean
}

internal class PcmRecordingSessionRegistry {
    var active: PcmRecordingSession? = null
        private set

    fun activate(session: PcmRecordingSession) {
        check(active == null) { "PCM recording session is already active" }
        active = session
        session.observeTerminalResult()
    }

    fun clear(session: PcmRecordingSession): Boolean {
        if (active !== session) return false
        active = null
        return true
    }
}

internal class PcmRecordingSessionOrchestrator(
    private val engine: PcmRecordingEngine,
    private val target: PendingAudioRecording,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val onSettling: () -> Unit,
    private val persistStoppedRecording: suspend (
        PendingAudioRecording,
        stoppedAtMillis: Long,
    ) -> CompletedRecordingWriteResult,
    private val destroyPublishTimeoutMillis: Long = DESTROY_PUBLISH_TIMEOUT_MILLIS,
    private val onOutcome: (PcmRecordingSessionOutcome) -> Unit,
) : PcmRecordingSession {
    private val settlementClaimed = AtomicBoolean(false)
    private var terminalObserver: Job? = null

    override fun observeTerminalResult() {
        terminalObserver = scope.launch {
            when (val terminal = engine.terminalResult.await()) {
                PcmRecordingTerminalResult.Completed -> Unit
                is PcmRecordingTerminalResult.Failed -> settleFailure()
            }
        }
    }

    override fun requestStop(stoppedAtMillis: Long): Boolean {
        if (!claimSettlement(cancelTerminalObserver = true)) return false
        launchSettlement {
            val stopResult = runCatching { engine.stop() }
            if (stopResult.isFailure) {
                return@launchSettlement settleFailedSession()
            }
            when (runCatching {
                persistStoppedRecording(target, stoppedAtMillis)
            }.getOrElse { return@launchSettlement settleFailedSession() }) {
                CompletedRecordingWriteResult.Stored -> {
                    PcmRecordingSessionOutcome.Stopped(target, stoppedAtMillis)
                }

                CompletedRecordingWriteResult.PublishFailed -> {
                    PcmRecordingSessionOutcome.PublishFailed
                }

                CompletedRecordingWriteResult.RepositoryFailed -> {
                    PcmRecordingSessionOutcome.RepositoryFailed
                }
            }
        }
        return true
    }

    override fun requestDestroy(): Boolean {
        if (!claimSettlement(cancelTerminalObserver = true)) return false
        launchSettlement {
            val stopResult = runCatching { engine.stop() }
            if (stopResult.isFailure) {
                return@launchSettlement settleFailedSession()
            }
            val publishResult = runCatching {
                withTimeout(destroyPublishTimeoutMillis) {
                    runInterruptible { target.publish() }
                }
            }
            if (publishResult.isSuccess) {
                PcmRecordingSessionOutcome.Interrupted
            } else {
                settleFailedSession()
            }
        }
        return true
    }

    private suspend fun settleFailure() {
        if (!claimSettlement(cancelTerminalObserver = false)) return
        launchSettlement { settleFailedSession() }
    }

    private suspend fun settleFailedSession(): PcmRecordingSessionOutcome {
        runCatching { engine.release() }
        runCatching {
            withTimeout(CLEANUP_TIMEOUT_MILLIS) {
                runInterruptible { target.discard() }
            }
        }
        return PcmRecordingSessionOutcome.Failed
    }

    private fun launchSettlement(settleOnIo: suspend () -> PcmRecordingSessionOutcome) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                val outcome = withContext(ioDispatcher) { settleOnIo() }
                onOutcome(outcome)
            }
        }
    }

    private fun claimSettlement(cancelTerminalObserver: Boolean): Boolean {
        if (!settlementClaimed.compareAndSet(false, true)) return false
        if (cancelTerminalObserver) terminalObserver?.cancel()
        onSettling()
        return true
    }

    private companion object {
        const val DESTROY_PUBLISH_TIMEOUT_MILLIS = 15_000L
        const val CLEANUP_TIMEOUT_MILLIS = 20_000L
    }
}
