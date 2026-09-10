package com.percontext.app.domain.asr

import com.percontext.app.domain.recording.RecordingSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SingleSessionAsrProvider(
    private val delegate: AsrProvider,
    private val recordingSession: StateFlow<RecordingSession>,
) : AsrProvider {
    private val sessionMutex = Mutex()

    override suspend fun transcribe(audioLocation: String): TranscriptResult = sessionMutex.withLock {
        recordingSession.first(RecordingSession::allowsAsrStart)
        delegate.transcribe(audioLocation)
    }
}

private fun RecordingSession.allowsAsrStart(): Boolean =
    this == RecordingSession.Idle || this is RecordingSession.Failed
