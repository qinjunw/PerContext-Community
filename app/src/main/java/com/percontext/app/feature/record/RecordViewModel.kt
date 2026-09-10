package com.percontext.app.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.percontext.app.core.media.PlaybackController
import com.percontext.app.core.media.PlaybackState
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.model.VoiceRecord
import com.percontext.app.domain.recording.RecordingFeatureGateway
import com.percontext.app.domain.recording.RecordingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordUiState(
    val records: List<VoiceRecord> = emptyList(),
    val recordingSession: RecordingSession = RecordingSession.Idle,
    val recordingInputLevel: Float = 0f,
    val playback: PlaybackState = PlaybackState(),
)

class RecordViewModel(
    private val gateway: RecordingFeatureGateway,
    private val playbackController: PlaybackController,
) : ViewModel() {
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val messages: Flow<String> = mutableMessages
    val uiState: StateFlow<RecordUiState> = combine(
        gateway.records,
        gateway.recordingSession,
        gateway.recordingInputLevel,
        playbackController.state,
    ) { records, session, inputLevel, playback ->
        RecordUiState(records, session, inputLevel, playback)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = RecordUiState(),
    )

    fun startRecording() {
        runCatching { gateway.startRecording() }
            .onFailure { mutableMessages.tryEmit("无法启动录音服务") }
    }

    fun stopRecording() {
        runCatching { gateway.stopRecording() }
            .onFailure { mutableMessages.tryEmit("无法停止录音服务") }
    }

    fun microphonePermissionDenied() {
        mutableMessages.tryEmit("需要麦克风权限才能开始记录")
    }

    fun notificationPermissionDenied() {
        mutableMessages.tryEmit("通知权限未开启；录音状态不会显示在通知栏")
    }

    fun recordingStoragePermissionDenied() {
        mutableMessages.tryEmit("需要存储权限才能把录音保存到 Download")
    }

    fun togglePlayback(record: VoiceRecord) {
        playbackController.toggle(record)
    }

    fun seekPlayback(recordId: String, fraction: Float) {
        playbackController.seekTo(recordId, fraction)
    }

    fun requestTranscription(record: VoiceRecord) {
        if (record.transcriptStatus == TranscriptStatus.QUEUED ||
            record.transcriptStatus == TranscriptStatus.PROCESSING ||
            record.transcriptStatus == TranscriptStatus.SUCCEEDED
        ) {
            return
        }
        if (!gateway.isTranscriptionAvailable()) {
            mutableMessages.tryEmit("SenseVoice 本地模型尚未安装或不可用")
            return
        }

        viewModelScope.launch {
            try {
                gateway.requestTranscription(record.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                mutableMessages.emit("未能启动本地转写，录音已保留")
            }
        }
    }

    fun delete(record: VoiceRecord) {
        playbackController.stop(record.id)
        viewModelScope.launch {
            runCatching { gateway.deleteRecording(record.id) }
                .onSuccess { deleted ->
                    if (!deleted) mutableMessages.emit("未能删除这段录音")
                }
                .onFailure { mutableMessages.emit("未能删除这段录音") }
        }
    }

    override fun onCleared() {
        playbackController.release()
    }
}
