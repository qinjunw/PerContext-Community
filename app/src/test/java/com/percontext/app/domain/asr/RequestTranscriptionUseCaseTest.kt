package com.percontext.app.domain.asr

import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.repository.TranscriptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestTranscriptionUseCaseTest {
    @Test
    fun `request persists queued before starting new work`() = runTest {
        val transcripts = RequestTranscriptRepository()
        val scheduler = RequestScheduler()
        val request = RequestTranscriptionUseCase(transcripts, scheduler)

        val started = request(RECORD_ID)

        assertTrue(started)
        assertEquals(listOf(TranscriptStatus.QUEUED), transcripts.statuses)
        assertEquals(listOf(RECORD_ID), scheduler.requests)
    }

    @Test
    fun `enqueue failure marks request failed instead of leaving queued`() = runTest {
        val transcripts = RequestTranscriptRepository()
        val scheduler = RequestScheduler(failure = IllegalStateException("enqueue failed"))
        val request = RequestTranscriptionUseCase(transcripts, scheduler)

        val result = runCatching { request(RECORD_ID) }

        assertTrue(result.isFailure)
        assertEquals(
            listOf(TranscriptStatus.QUEUED, TranscriptStatus.FAILED),
            transcripts.statuses,
        )
    }

    @Test
    fun `enqueue cancellation marks request failed and propagates`() = runTest {
        val transcripts = RequestTranscriptRepository()
        val scheduler = RequestScheduler(failure = CancellationException("cancelled"))
        val request = RequestTranscriptionUseCase(transcripts, scheduler)

        val failure = runCatching { request(RECORD_ID) }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(
            listOf(TranscriptStatus.QUEUED, TranscriptStatus.FAILED),
            transcripts.statuses,
        )
    }

    @Test
    fun `request already active does not enqueue a second work item`() = runTest {
        val transcripts = RequestTranscriptRepository()
        val scheduler = RequestScheduler()
        val request = RequestTranscriptionUseCase(transcripts, scheduler)

        val firstStarted = request(RECORD_ID)
        val secondStarted = request(RECORD_ID)

        assertTrue(firstStarted)
        assertFalse(secondStarted)
        assertEquals(1, scheduler.requests.size)
    }

    private companion object {
        const val RECORD_ID = "record_1"
    }
}

private class RequestScheduler(
    private val failure: Throwable? = null,
) : TranscriptionScheduler {
    val requests = mutableListOf<String>()

    override suspend fun enqueue(recordId: String) {
        requests += recordId
        failure?.let { throw it }
    }
}

private class RequestTranscriptRepository(
    private var requestable: Boolean = true,
) : TranscriptRepository {
    val statuses = mutableListOf<TranscriptStatus>()

    override suspend fun queueIfRequestable(recordId: String): Boolean {
        if (!requestable) return false
        requestable = false
        statuses += TranscriptStatus.QUEUED
        return true
    }

    override suspend fun setStatus(recordId: String, status: TranscriptStatus): Boolean {
        statuses += status
        return true
    }

    override suspend fun saveSuccessful(recordId: String, result: TranscriptResult) = Unit
}
