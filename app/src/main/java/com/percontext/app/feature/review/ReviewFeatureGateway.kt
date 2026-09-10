package com.percontext.app.feature.review

import com.percontext.app.domain.dailycontext.DailyContext
import com.percontext.app.domain.dailycontext.DailyContextRequestResult
import com.percontext.app.domain.dailycontext.TranscribedRecordStamp
import com.percontext.app.domain.model.TranscriptStatus
import kotlinx.coroutines.flow.Flow

data class ReviewRecordingSummary(
    val recordId: String,
    val recordedAtMillis: Long,
    val durationMillis: Long,
    val transcriptStatus: TranscriptStatus,
)

interface ReviewFeatureGateway {
    val dailyContexts: Flow<List<DailyContext>>
    val transcribedRecords: Flow<List<TranscribedRecordStamp>>
    val recordingSummaries: Flow<List<ReviewRecordingSummary>>

    suspend fun requestDailyContext(
        dayKey: String,
        zoneId: String,
        allowChangedInput: Boolean = false,
    ): DailyContextRequestResult
}
