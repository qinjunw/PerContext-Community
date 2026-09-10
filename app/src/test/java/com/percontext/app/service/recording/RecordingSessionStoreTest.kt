package com.percontext.app.service.recording

import com.percontext.app.domain.recording.RecordingEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingSessionStoreTest {
    @Test
    fun `input level is exposed while recording and reset when recording stops`() {
        val store = RecordingSessionStore()

        store.dispatch(RecordingEvent.StartRequested)
        store.dispatch(RecordingEvent.Started(startedAtMillis = 1_000L))
        store.updateInputLevel(0.6f)

        assertEquals(0.6f, store.inputLevel.value)

        store.dispatch(RecordingEvent.StopRequested)

        assertEquals(0f, store.inputLevel.value)
    }

    @Test
    fun `input level stays within normalized range`() {
        val store = RecordingSessionStore()
        store.dispatch(RecordingEvent.StartRequested)
        store.dispatch(RecordingEvent.Started(startedAtMillis = 1_000L))

        store.updateInputLevel(1.4f)
        assertEquals(1f, store.inputLevel.value)

        store.updateInputLevel(-0.2f)
        assertEquals(0f, store.inputLevel.value)
    }
}
