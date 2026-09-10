package com.percontext.app.data.db

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "daily_contexts",
    primaryKeys = ["dayKey"],
)
data class DailyContextEntity(
    val dayKey: String,
    val zoneId: String,
    val title: String?,
    val summary: String?,
    val structuredJson: String?,
    val schemaVersion: String,
    val providerId: String?,
    val model: String?,
    val status: String,
    val inputFingerprint: String?,
    val generatedAtMillis: Long?,
    val updatedAtMillis: Long,
    val failureCode: String?,
)

@Entity(
    tableName = "daily_context_sources",
    primaryKeys = ["dayKey", "recordId"],
    foreignKeys = [
        ForeignKey(
            entity = DailyContextEntity::class,
            parentColumns = ["dayKey"],
            childColumns = ["dayKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TranscriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcriptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["recordId"]),
        Index(value = ["transcriptId"]),
    ],
)
data class DailyContextSourceEntity(
    val dayKey: String,
    val recordId: String,
    val transcriptId: String,
    val position: Int,
    val recordedAtMillis: Long,
)

data class DailyContextRow(
    @Embedded val context: DailyContextEntity,
    val sourceCount: Int,
)

data class DailyTranscriptRow(
    val recordId: String,
    val transcriptId: String,
    val recordedAtMillis: Long,
    val text: String,
)

data class TranscribedRecordStampRow(
    val recordId: String,
    val recordedAtMillis: Long,
)
