package com.percontext.app.service.asr

import android.content.pm.ServiceInfo
import com.percontext.app.domain.asr.TranscriptResult
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.repository.TranscriptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionWorkerExecutionTest {
    @Test
    fun `android 14 uses data sync foreground service type`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            transcriptionForegroundServiceType(sdkInt = 34),
        )
    }

    @Test
    fun `android 15 uses media processing foreground service type`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            transcriptionForegroundServiceType(sdkInt = 35),
        )
    }

    @Test
    fun `worker enters foreground before transcription`() = runTest {
        val events = mutableListOf<String>()

        val succeeded = executeTranscriptionWorker(
            recordId = RECORD_ID,
            transcriptRepository = WorkerTranscriptRepository(),
            enterForeground = { events += "foreground" },
            operation = { events += "transcription" },
        )

        assertTrue(succeeded)
        assertEquals(listOf("foreground", "transcription"), events)
    }

    @Test
    fun `foreground failure does not start transcription`() = runTest {
        var transcriptionStarted = false
        val transcriptRepository = WorkerTranscriptRepository()

        val succeeded = executeTranscriptionWorker(
            recordId = RECORD_ID,
            transcriptRepository = transcriptRepository,
            enterForeground = { error("foreground unavailable") },
            operation = { transcriptionStarted = true },
        )

        assertFalse(succeeded)
        assertFalse(transcriptionStarted)
        assertEquals(TranscriptStatus.FAILED, transcriptRepository.status)
    }

    @Test
    fun `foreground cancellation restores requestable status without starting transcription`() = runTest {
        var transcriptionStarted = false
        val transcriptRepository = WorkerTranscriptRepository()
        val promotionStarted = CompletableDeferred<Unit>()

        val request = launch {
            executeTranscriptionWorker(
                recordId = RECORD_ID,
                transcriptRepository = transcriptRepository,
                enterForeground = {
                    promotionStarted.complete(Unit)
                    awaitCancellation()
                },
                operation = { transcriptionStarted = true },
            )
        }
        promotionStarted.await()
        request.cancelAndJoin()

        assertTrue(request.isCancelled)
        assertFalse(transcriptionStarted)
        assertEquals(TranscriptStatus.NOT_REQUESTED, transcriptRepository.status)
    }

    @Test
    fun `foreground cleanup failure is suppressed onto original cancellation`() = runTest {
        val cancellation = CancellationException("promotion cancelled")
        val cleanupFailure = IllegalStateException("status cleanup failed")
        val transcriptRepository = WorkerTranscriptRepository(statusFailure = cleanupFailure)
        var transcriptionStarted = false

        val thrown = runCatching {
            executeTranscriptionWorker(
                recordId = RECORD_ID,
                transcriptRepository = transcriptRepository,
                enterForeground = { throw cancellation },
                operation = { transcriptionStarted = true },
            )
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(1, thrown?.suppressed?.size)
        assertTrue(thrown?.suppressed?.single() is IllegalStateException)
        assertEquals(cleanupFailure.message, thrown?.suppressed?.single()?.message)
        assertFalse(transcriptionStarted)
        assertEquals(TranscriptStatus.QUEUED, transcriptRepository.status)
    }

    @Test
    fun `worker cancellation remains cancellation`() = runTest {
        val result = runCatching {
            executeTranscriptionWorker(
                recordId = RECORD_ID,
                transcriptRepository = WorkerTranscriptRepository(),
                enterForeground = {},
                operation = { throw CancellationException("cancelled") },
            )
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `worker maps ordinary exception to failure`() = runTest {
        val succeeded = executeTranscriptionWorker(
            recordId = RECORD_ID,
            transcriptRepository = WorkerTranscriptRepository(),
            enterForeground = {},
            operation = { error("provider failed") },
        )

        assertFalse(succeeded)
    }

    private companion object {
        const val RECORD_ID = "record-1"
    }
}

private class WorkerTranscriptRepository(
    private val statusFailure: Throwable? = null,
) : TranscriptRepository {
    var status = TranscriptStatus.QUEUED
        private set

    override suspend fun queueIfRequestable(recordId: String): Boolean = false

    override suspend fun setStatus(recordId: String, status: TranscriptStatus): Boolean {
        currentCoroutineContext().ensureActive()
        statusFailure?.let { throw it }
        this.status = status
        return true
    }

    override suspend fun saveSuccessful(recordId: String, result: TranscriptResult) = Unit
}
