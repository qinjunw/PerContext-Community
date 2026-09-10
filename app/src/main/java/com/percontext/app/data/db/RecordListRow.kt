package com.percontext.app.data.db

import androidx.room3.Embedded

data class RecordListRow(
    @Embedded val record: RecordEntity,
    val transcriptText: String?,
)
