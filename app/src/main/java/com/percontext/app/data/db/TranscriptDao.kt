package com.percontext.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
interface TranscriptDao {
    @Query(
        """
        UPDATE records
        SET transcriptStatus = 'QUEUED'
        WHERE id = :recordId
          AND transcriptStatus IN ('NOT_REQUESTED', 'FAILED')
        """,
    )
    suspend fun queueIfRequestable(recordId: String): Int

    @Query("UPDATE records SET transcriptStatus = :status WHERE id = :recordId")
    suspend fun updateStatus(recordId: String, status: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTranscriptIfAbsent(transcript: TranscriptEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Query("SELECT * FROM transcripts WHERE recordId = :recordId LIMIT 1")
    suspend fun findByRecordId(recordId: String): TranscriptEntity?

    @Query("SELECT * FROM transcript_segments WHERE transcriptId = :transcriptId ORDER BY position")
    suspend fun findSegments(transcriptId: String): List<TranscriptSegmentEntity>

    @Transaction
    suspend fun saveSuccessfulTranscriptIfAbsent(
        transcript: TranscriptEntity,
        segments: List<TranscriptSegmentEntity>,
    ) {
        val inserted = insertTranscriptIfAbsent(transcript) != -1L
        if (inserted && segments.isNotEmpty()) insertSegments(segments)
        check(updateStatus(transcript.recordId, "SUCCEEDED") == 1) {
            "Cannot save transcript for missing record ${transcript.recordId}"
        }
    }
}
