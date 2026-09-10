package com.percontext.app.service.recording

import com.percontext.app.domain.recording.RecordingEvent
import com.percontext.app.domain.recording.RecordingSession
import com.percontext.app.domain.recording.RecordingStateMachine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecordingSessionStore {
    private val stateMachine = RecordingStateMachine()
    private val mutableSession = MutableStateFlow<RecordingSession>(RecordingSession.Idle)
    private val mutableInputLevel = MutableStateFlow(0f)

    val session: StateFlow<RecordingSession> = mutableSession.asStateFlow()
    val inputLevel: StateFlow<Float> = mutableInputLevel.asStateFlow()

    fun dispatch(event: RecordingEvent) = synchronized(this) {
        mutableSession.value = stateMachine.reduce(mutableSession.value, event)
        if (mutableSession.value !is RecordingSession.Recording) {
            mutableInputLevel.value = 0f
        }
    }

    fun updateInputLevel(level: Float) = synchronized(this) {
        mutableInputLevel.value = if (mutableSession.value is RecordingSession.Recording) {
            level.coerceIn(0f, 1f)
        } else {
            0f
        }
    }
}
