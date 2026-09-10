package com.percontext.app.data.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["recordId"], unique = true)],
)
data class TranscriptEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val rawText: String,
    val language: String?,
    val provider: String,
    val model: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcriptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["transcriptId"])],
)
data class TranscriptSegmentEntity(
    @PrimaryKey val id: String,
    val transcriptId: String,
    val position: Int,
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
)
