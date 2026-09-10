package com.percontext.app.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.ColumnInfo

@Entity(
    tableName = "records",
    indices = [Index(value = ["createdAtMillis"])],
)
data class RecordEntity(
    @PrimaryKey val id: String,
    val createdAtMillis: Long,
    val durationMillis: Long,
    @ColumnInfo(name = "audioPath") val audioLocation: String,
    val audioCodec: String,
    val recordStatus: String,
    val transcriptStatus: String,
    val contextStatus: String,
)
