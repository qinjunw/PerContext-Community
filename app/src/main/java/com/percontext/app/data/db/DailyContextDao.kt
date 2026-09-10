package com.percontext.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyContextDao {
    @Query(
        """
        SELECT daily_contexts.*, COUNT(daily_context_sources.recordId) AS sourceCount
        FROM daily_contexts
        LEFT JOIN daily_context_sources
          ON daily_context_sources.dayKey = daily_contexts.dayKey
        GROUP BY daily_contexts.dayKey
        ORDER BY daily_contexts.dayKey DESC
        """,
    )
    fun observeAll(): Flow<List<DailyContextRow>>

    @Query("SELECT * FROM daily_contexts WHERE dayKey = :dayKey LIMIT 1")
    suspend fun findEntity(dayKey: String): DailyContextEntity?

    @Query(
        """
        SELECT daily_contexts.*, COUNT(daily_context_sources.recordId) AS sourceCount
        FROM daily_contexts
        LEFT JOIN daily_context_sources
          ON daily_context_sources.dayKey = daily_contexts.dayKey
        WHERE daily_contexts.dayKey = :dayKey
        GROUP BY daily_contexts.dayKey
        LIMIT 1
        """,
    )
    suspend fun find(dayKey: String): DailyContextRow?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(context: DailyContextEntity): Long

    @Update
    suspend fun update(context: DailyContextEntity): Int

    @Query("DELETE FROM daily_context_sources WHERE dayKey = :dayKey")
    suspend fun deleteSources(dayKey: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSources(sources: List<DailyContextSourceEntity>)

    @Transaction
    suspend fun upsert(context: DailyContextEntity) {
        if (insertIfAbsent(context) == -1L) {
            check(update(context) == 1) { "Cannot update daily context ${context.dayKey}" }
        }
    }

    @Transaction
    suspend fun saveWithSources(
        context: DailyContextEntity,
        sources: List<DailyContextSourceEntity>,
    ) {
        upsert(context)
        deleteSources(context.dayKey)
        if (sources.isNotEmpty()) insertSources(sources)
    }
}

@Dao
interface DailyContextSourceDao {
    @Query(
        """
        SELECT records.id AS recordId, records.createdAtMillis AS recordedAtMillis
        FROM records
        INNER JOIN transcripts ON transcripts.recordId = records.id
        ORDER BY records.createdAtMillis
        """,
    )
    fun observeTranscribedRecords(): Flow<List<TranscribedRecordStampRow>>

    @Query(
        """
        SELECT
          records.id AS recordId,
          transcripts.id AS transcriptId,
          records.createdAtMillis AS recordedAtMillis,
          transcripts.rawText AS text
        FROM records
        INNER JOIN transcripts ON transcripts.recordId = records.id
        WHERE records.createdAtMillis >= :startMillis
          AND records.createdAtMillis < :endExclusiveMillis
        ORDER BY records.createdAtMillis
        """,
    )
    suspend fun findBetween(
        startMillis: Long,
        endExclusiveMillis: Long,
    ): List<DailyTranscriptRow>
}
