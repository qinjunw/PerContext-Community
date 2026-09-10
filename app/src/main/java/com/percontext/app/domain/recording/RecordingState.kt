package com.percontext.app.domain.recording

sealed interface RecordingSession {
    data object Idle : RecordingSession

    data object Starting : RecordingSession

    data class Recording(
        val startedAtMillis: Long,
    ) : RecordingSession

    data object Stopping : RecordingSession

    data class Failed(
        val reason: String,
    ) : RecordingSession
}

sealed interface RecordingEvent {
    data object StartRequested : RecordingEvent

    data class Started(
        val startedAtMillis: Long,
    ) : RecordingEvent

    data object StopRequested : RecordingEvent

    data object Stored : RecordingEvent

    data class Failed(
        val reason: String,
    ) : RecordingEvent
}

class RecordingStateMachine {
    fun reduce(
        current: RecordingSession,
        event: RecordingEvent,
    ): RecordingSession = when {
        event is RecordingEvent.Failed -> RecordingSession.Failed(event.reason)
        event == RecordingEvent.StartRequested &&
            (current == RecordingSession.Idle || current is RecordingSession.Failed) -> {
            RecordingSession.Starting
        }
        event is RecordingEvent.Started && current == RecordingSession.Starting -> {
            RecordingSession.Recording(event.startedAtMillis)
        }
        event == RecordingEvent.StopRequested && current is RecordingSession.Recording -> {
            RecordingSession.Stopping
        }
        event == RecordingEvent.Stored && current == RecordingSession.Stopping -> RecordingSession.Idle
        else -> current
    }
}
