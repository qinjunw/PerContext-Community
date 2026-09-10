package com.percontext.app.service.dailycontext

import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyContextWorkerExecutionTest {
    @Test(expected = CancellationException::class)
    fun `worker execution propagates cancellation`() = runTest {
        executeDailyContextWorker { throw CancellationException("cancelled") }
    }

    @Test
    fun `worker execution reports ordinary provider failure`() = runTest {
        assertFalse(executeDailyContextWorker { error("provider unavailable") })
    }

    @Test
    fun `worker execution reports provider failure to diagnostic callback`() = runTest {
        var observed: Throwable? = null

        val succeeded = executeDailyContextWorker(
            operation = { error("provider unavailable") },
            onFailure = { observed = it },
        )

        assertFalse(succeeded)
        assertEquals("provider unavailable", observed?.message)
    }

    @Test
    fun `worker execution reports success`() = runTest {
        assertTrue(executeDailyContextWorker { })
    }
}
