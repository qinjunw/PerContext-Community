package com.percontext.app.service.recording

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmInputShutdownRequesterTest {
    @Test
    fun `blocking native shutdown requests run on isolated daemons exactly once`() {
        val stopEntered = CountDownLatch(1)
        val releaseEntered = CountDownLatch(1)
        val stopCount = AtomicInteger()
        val releaseCount = AtomicInteger()
        val requester = PcmInputShutdownRequester(
            stopInput = permanentlyBlocking(stopCount, stopEntered),
            releaseInput = permanentlyBlocking(releaseCount, releaseEntered),
        )

        val startedAtNanos = System.nanoTime()
        requester.requestStop()
        requester.requestStop()
        requester.requestRelease()
        requester.requestRelease()
        val requestDurationMillis = TimeUnit.NANOSECONDS.toMillis(
            System.nanoTime() - startedAtNanos,
        )

        assertTrue(stopEntered.await(1, TimeUnit.SECONDS))
        assertTrue(releaseEntered.await(1, TimeUnit.SECONDS))
        assertTrue(
            "shutdown requests blocked for ${requestDurationMillis}ms",
            requestDurationMillis < 500L,
        )
        assertEquals(1, stopCount.get())
        assertEquals(1, releaseCount.get())
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
                // Exercise the platform failure mode: the native call ignores interruption.
            }
        }
    }
}
