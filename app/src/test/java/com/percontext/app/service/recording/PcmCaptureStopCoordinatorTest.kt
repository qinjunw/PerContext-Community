package com.percontext.app.service.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PcmCaptureStopCoordinatorTest {
    @Test
    fun `graceful completion relinquishes ownership without forced release`() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(
            completionResults = ArrayDeque(listOf(true)),
            calls = calls,
        )

        coordinator.stop()

        assertEquals(
            listOf("request-stop", "await-15000", "relinquish"),
            calls,
        )
    }

    @Test
    fun `graceful timeout forces release and waits once more before returning`() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(
            completionResults = ArrayDeque(listOf(false, true)),
            calls = calls,
        )

        coordinator.stop()

        assertEquals(
            listOf(
                "request-stop",
                "await-15000",
                "force-release",
                "await-2000",
                "relinquish",
            ),
            calls,
        )
    }

    @Test
    fun `forced timeout fails after bounded waits and still relinquishes ownership`() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(
            completionResults = ArrayDeque(listOf(false, false)),
            calls = calls,
        )

        assertThrows(IllegalStateException::class.java) {
            coordinator.stop()
        }

        assertEquals(
            listOf(
                "request-stop",
                "await-15000",
                "force-release",
                "await-2000",
                "relinquish",
            ),
            calls,
        )
    }

    @Test
    fun `graceful stop failure still forces release before relinquishing ownership`() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(
            completionResults = ArrayDeque(listOf(true)),
            calls = calls,
            requestStopFailure = IllegalStateException("stop failed"),
        )

        coordinator.stop()

        assertEquals(
            listOf(
                "request-stop",
                "force-release",
                "await-2000",
                "relinquish",
            ),
            calls,
        )
    }

    private fun coordinator(
        completionResults: ArrayDeque<Boolean>,
        calls: MutableList<String>,
        requestStopFailure: Throwable? = null,
    ) = PcmCaptureStopCoordinator(
        requestStop = {
            calls += "request-stop"
            requestStopFailure?.let { throw it }
        },
        awaitCompletion = { timeoutMillis ->
            calls += "await-$timeoutMillis"
            completionResults.removeFirst()
        },
        forceRelease = { calls += "force-release" },
        relinquishOwnership = { calls += "relinquish" },
        gracefulTimeoutMillis = 15_000L,
        forcedTimeoutMillis = 2_000L,
    )
}
