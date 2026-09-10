package com.percontext.app.service.asr

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkManagerTranscriptionSchedulerTest {
    @Test
    fun `manual request replaces stale work for the same record`() {
        assertEquals(ExistingWorkPolicy.REPLACE, TRANSCRIPTION_WORK_POLICY)
    }

    @Test
    fun `work name is stable per record`() {
        assertEquals("local_transcription_record_1", transcriptionWorkName("record_1"))
    }
}
