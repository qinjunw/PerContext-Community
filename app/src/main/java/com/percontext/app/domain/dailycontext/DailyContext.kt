package com.percontext.app.domain.dailycontext

data class DailyContext(
    val dayKey: String,
    val zoneId: String,
    val content: DailyContextContent?,
    val structuredJson: String?,
    val schemaVersion: String = DAILY_CONTEXT_SCHEMA_VERSION,
    val providerId: String?,
    val model: String?,
    val status: DailyContextStatus,
    val inputFingerprint: String?,
    val sourceCount: Int,
    val generatedAtMillis: Long?,
    val updatedAtMillis: Long,
    val failureCode: DailyContextFailureCode? = null,
)

data class DailyContextContent(
    val title: String,
    val summary: String,
    val topics: List<String>,
    val ideas: List<String>,
    val questions: List<String>,
    val decisions: List<String>,
    val todos: List<String>,
)

data class DailyContextSource(
    val recordId: String,
    val transcriptId: String,
    val recordedAtMillis: Long,
    val text: String,
    val position: Int = 0,
)

data class TranscribedRecordStamp(
    val recordId: String,
    val recordedAtMillis: Long,
)

enum class DailyContextStatus {
    QUEUED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
}

enum class DailyContextFailureCode {
    NO_TRANSCRIPTS,
    MISSING_API_KEY,
    PROVIDER_ERROR,
    INVALID_RESPONSE,
    ENQUEUE_FAILED,
    CANCELLED,
}

const val DAILY_CONTEXT_SCHEMA_VERSION = "daily_context.v1"
