package com.percontext.app.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingStateMachineTest {
    private val machine = RecordingStateMachine()

    @Test
    fun `start request moves idle session to starting`() {
        val next = machine.reduce(RecordingSession.Idle, RecordingEvent.StartRequested)

        assertEquals(RecordingSession.Starting, next)
    }

    @Test
    fun `recorder start stores start time`() {
        val next = machine.reduce(RecordingSession.Starting, RecordingEvent.Started(1_000L))

        assertEquals(RecordingSession.Recording(startedAtMillis = 1_000L), next)
    }

    @Test
    fun `stop and persistence return session to idle`() {
        val stopping = machine.reduce(
            RecordingSession.Recording(startedAtMillis = 1_000L),
            RecordingEvent.StopRequested,
        )
        val stored = machine.reduce(stopping, RecordingEvent.Stored)

        assertEquals(RecordingSession.Stopping, stopping)
        assertEquals(RecordingSession.Idle, stored)
    }

    @Test
    fun `invalid event keeps current session`() {
        val next = machine.reduce(RecordingSession.Idle, RecordingEvent.StopRequested)

        assertEquals(RecordingSession.Idle, next)
    }

    @Test
    fun `failure exposes a user safe reason`() {
        val next = machine.reduce(
            RecordingSession.Starting,
            RecordingEvent.Failed("麦克风被其他应用占用"),
        )

        assertEquals(RecordingSession.Failed("麦克风被其他应用占用"), next)
    }
}
