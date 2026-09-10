package com.percontext.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<RecordEntity>>

    @Query(
        """
        SELECT records.*, transcripts.rawText AS transcriptText
        FROM records
        LEFT JOIN transcripts ON transcripts.recordId = records.id
        ORDER BY records.createdAtMillis DESC
        """,
    )
    fun observeAllWithTranscripts(): Flow<List<RecordListRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: RecordEntity)

    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RecordEntity?

    @Query(
        """
        SELECT records.*, transcripts.rawText AS transcriptText
        FROM records
        LEFT JOIN transcripts ON transcripts.recordId = records.id
        WHERE records.id = :id
        LIMIT 1
        """,
    )
    suspend fun findWithTranscriptById(id: String): RecordListRow?

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
