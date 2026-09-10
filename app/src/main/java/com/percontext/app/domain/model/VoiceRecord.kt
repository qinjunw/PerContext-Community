package com.percontext.app.domain.model

data class VoiceRecord(
    val id: String,
    val createdAtMillis: Long,
    val durationMillis: Long,
    val audioLocation: String,
    val audioCodec: String,
    val status: RecordStatus,
    val fileSizeBytes: Long? = null,
    val transcriptStatus: TranscriptStatus = TranscriptStatus.NOT_REQUESTED,
    val transcriptText: String? = null,
)

enum class RecordStatus {
    RECORDING,
    RECORDED,
    CORRUPTED,
}

enum class TranscriptStatus {
    NOT_REQUESTED,
    QUEUED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
}
