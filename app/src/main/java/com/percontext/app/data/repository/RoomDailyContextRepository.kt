package com.percontext.app.data.repository

import com.percontext.app.data.db.DailyContextDao
import com.percontext.app.data.db.DailyContextEntity
import com.percontext.app.data.db.DailyContextRow
import com.percontext.app.data.db.DailyContextSourceDao
import com.percontext.app.data.db.DailyContextSourceEntity
import com.percontext.app.domain.dailycontext.DAILY_CONTEXT_SCHEMA_VERSION
import com.percontext.app.domain.dailycontext.DailyContext
import com.percontext.app.domain.dailycontext.DailyContextFailureCode
import com.percontext.app.domain.dailycontext.DailyContextRepository
import com.percontext.app.domain.dailycontext.DailyContextResponseParser
import com.percontext.app.domain.dailycontext.DailyContextSource
import com.percontext.app.domain.dailycontext.DailyContextSourceRepository
import com.percontext.app.domain.dailycontext.DailyContextStatus
import com.percontext.app.domain.dailycontext.TranscribedRecordStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class RoomDailyContextRepository(
    private val dao: DailyContextDao,
    private val parser: DailyContextResponseParser = DailyContextResponseParser(),
) : DailyContextRepository {
    override val contexts: Flow<List<DailyContext>> = dao.observeAll()
        .map { rows -> rows.map(::toDomain) }
        .flowOn(Dispatchers.IO)

    override suspend fun find(dayKey: String): DailyContext? = dao.find(dayKey)?.let(::toDomain)

    override suspend fun markQueued(dayKey: String, zoneId: String, updatedAtMillis: Long) {
        dao.upsert(statusEntity(dayKey, zoneId, DailyContextStatus.QUEUED, updatedAtMillis))
    }

    override suspend fun markProcessing(
        dayKey: String,
        zoneId: String,
        inputFingerprint: String,
        updatedAtMillis: Long,
    ) {
        val processing = statusEntity(dayKey, zoneId, DailyContextStatus.PROCESSING, updatedAtMillis)
        dao.upsert(
            processing.copy(
                inputFingerprint = if (processing.structuredJson == null) {
                    inputFingerprint
                } else {
                    processing.inputFingerprint
                },
            ),
        )
    }

    override suspend fun saveSuccessful(
        context: DailyContext,
        sources: List<DailyContextSource>,
    ) {
        requireNotNull(context.content)
        dao.saveWithSources(
            context = DailyContextEntity(
                dayKey = context.dayKey,
                zoneId = context.zoneId,
                title = context.content.title,
                summary = context.content.summary,
                structuredJson = context.structuredJson,
                schemaVersion = context.schemaVersion,
                providerId = context.providerId,
                model = context.model,
                status = context.status.name,
                inputFingerprint = context.inputFingerprint,
                generatedAtMillis = context.generatedAtMillis,
                updatedAtMillis = context.updatedAtMillis,
                failureCode = null,
            ),
            sources = sources.map { source ->
                DailyContextSourceEntity(
                    dayKey = context.dayKey,
                    recordId = source.recordId,
                    transcriptId = source.transcriptId,
                    position = source.position,
                    recordedAtMillis = source.recordedAtMillis,
                )
            },
        )
    }

    override suspend fun markFailed(
        dayKey: String,
        zoneId: String,
        failureCode: DailyContextFailureCode,
        updatedAtMillis: Long,
    ) {
        dao.upsert(
            statusEntity(dayKey, zoneId, DailyContextStatus.FAILED, updatedAtMillis)
                .copy(failureCode = failureCode.name),
        )
    }

    private suspend fun statusEntity(
        dayKey: String,
        zoneId: String,
        status: DailyContextStatus,
        updatedAtMillis: Long,
    ): DailyContextEntity = dao.findEntity(dayKey)?.copy(
        zoneId = zoneId,
        status = status.name,
        updatedAtMillis = updatedAtMillis,
        failureCode = null,
    ) ?: DailyContextEntity(
        dayKey = dayKey,
        zoneId = zoneId,
        title = null,
        summary = null,
        structuredJson = null,
        schemaVersion = DAILY_CONTEXT_SCHEMA_VERSION,
        providerId = null,
        model = null,
        status = status.name,
        inputFingerprint = null,
        generatedAtMillis = null,
        updatedAtMillis = updatedAtMillis,
        failureCode = null,
    )

    private fun toDomain(row: DailyContextRow): DailyContext {
        val entity = row.context
        val parsed = entity.structuredJson?.let { raw -> runCatching { parser.parse(raw).content }.getOrNull() }
        return DailyContext(
            dayKey = entity.dayKey,
            zoneId = entity.zoneId,
            content = parsed,
            structuredJson = entity.structuredJson,
            schemaVersion = entity.schemaVersion,
            providerId = entity.providerId,
            model = entity.model,
            status = DailyContextStatus.valueOf(entity.status),
            inputFingerprint = entity.inputFingerprint,
            sourceCount = row.sourceCount,
            generatedAtMillis = entity.generatedAtMillis,
            updatedAtMillis = entity.updatedAtMillis,
            failureCode = entity.failureCode?.let(DailyContextFailureCode::valueOf),
        )
    }
}

class RoomDailyContextSourceRepository(
    private val dao: DailyContextSourceDao,
) : DailyContextSourceRepository {
    override val transcribedRecords: Flow<List<TranscribedRecordStamp>> =
        dao.observeTranscribedRecords()
            .map { rows -> rows.map { TranscribedRecordStamp(it.recordId, it.recordedAtMillis) } }
            .flowOn(Dispatchers.IO)

    override suspend fun findBetween(
        startMillis: Long,
        endExclusiveMillis: Long,
    ): List<DailyContextSource> = dao.findBetween(startMillis, endExclusiveMillis)
        .mapIndexed { index, row ->
            DailyContextSource(
                recordId = row.recordId,
                transcriptId = row.transcriptId,
                recordedAtMillis = row.recordedAtMillis,
                text = row.text,
                position = index,
            )
        }
}
