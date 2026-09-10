package com.percontext.app.domain.repository

import com.percontext.app.domain.model.VoiceRecord
import kotlinx.coroutines.flow.Flow

interface VoiceRecordRepository {
    val records: Flow<List<VoiceRecord>>

    suspend fun add(record: VoiceRecord)

    suspend fun findById(id: String): VoiceRecord?

    suspend fun delete(id: String): Boolean
}
