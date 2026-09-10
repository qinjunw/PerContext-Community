package com.percontext.app.domain.dailycontext

import kotlinx.coroutines.flow.Flow

interface DailyContextRepository {
    val contexts: Flow<List<DailyContext>>

    suspend fun find(dayKey: String): DailyContext?

    suspend fun markQueued(dayKey: String, zoneId: String, updatedAtMillis: Long)

    suspend fun markProcessing(
        dayKey: String,
        zoneId: String,
        inputFingerprint: String,
        updatedAtMillis: Long,
    )

    suspend fun saveSuccessful(context: DailyContext, sources: List<DailyContextSource>)

    suspend fun markFailed(
        dayKey: String,
        zoneId: String,
        failureCode: DailyContextFailureCode,
        updatedAtMillis: Long,
    )
}

interface DailyContextSourceRepository {
    val transcribedRecords: Flow<List<TranscribedRecordStamp>>

    suspend fun findBetween(startMillis: Long, endExclusiveMillis: Long): List<DailyContextSource>
}

fun interface DailyContextScheduler {
    suspend fun enqueue(dayKey: String, zoneId: String)
}
