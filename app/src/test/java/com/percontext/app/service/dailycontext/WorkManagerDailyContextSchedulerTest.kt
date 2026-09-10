package com.percontext.app.service.dailycontext

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkManagerDailyContextSchedulerTest {
    @Test
    fun `same local day keeps a single generation task`() {
        assertEquals(ExistingWorkPolicy.KEEP, DAILY_CONTEXT_WORK_POLICY)
        assertEquals("daily_context_2026-08-27", dailyContextWorkName("2026-08-27"))
    }
}
