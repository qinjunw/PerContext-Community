package com.percontext.app.domain.recording

import com.percontext.app.domain.model.VoiceRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RecordingFeatureGateway {
    val records: Flow<List<VoiceRecord>>
    val recordingSession: StateFlow<RecordingSession>
    val recordingInputLevel: StateFlow<Float>

    fun startRecording()

    fun stopRecording()

    fun isTranscriptionAvailable(): Boolean

    suspend fun requestTranscription(recordId: String): Boolean

    suspend fun deleteRecording(recordId: String): Boolean
}
