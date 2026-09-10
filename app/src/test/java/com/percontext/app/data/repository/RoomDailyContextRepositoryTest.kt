package com.percontext.app.data.repository

import com.percontext.app.data.db.DailyContextDao
import com.percontext.app.data.db.DailyContextEntity
import com.percontext.app.data.db.DailyContextRow
import com.percontext.app.data.db.DailyContextSourceEntity
import com.percontext.app.domain.dailycontext.DailyContext
import com.percontext.app.domain.dailycontext.DailyContextContent
import com.percontext.app.domain.dailycontext.DailyContextFailureCode
import com.percontext.app.domain.dailycontext.DailyContextSource
import com.percontext.app.domain.dailycontext.DailyContextStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoomDailyContextRepositoryTest {
    @Test
    fun `successful context persists structured content and ordered sources`() = runTest {
        val dao = FakeDailyContextDao()
        val repository = RoomDailyContextRepository(dao)
        val context = successfulContext()

        repository.saveSuccessful(
            context,
            listOf(
                DailyContextSource("record_1", "transcript_1", 10L, "第一条", 0),
                DailyContextSource("record_2", "transcript_2", 20L, "第二条", 1),
            ),
        )

        val restored = repository.contexts.first().single()
        assertEquals("每日回顾", restored.content?.title)
        assertEquals(2, restored.sourceCount)
        assertEquals(listOf(0, 1), dao.sources.map { it.position })
    }

    @Test
    fun `successful context restores normalized summary paragraphs`() = runTest {
        val dao = FakeDailyContextDao()
        val repository = RoomDailyContextRepository(dao)
        val original = successfulContext()
        val context = original.copy(
            content = requireNotNull(original.content).copy(summary = "第一段。\n\n第二段。"),
            structuredJson = PARAGRAPH_JSON,
        )

        repository.saveSuccessful(context, emptyList())

        assertEquals("第一段。\n\n第二段。", repository.contexts.first().single().content?.summary)
    }

    @Test
    fun `failed refresh changes status without removing last successful content`() = runTest {
        val dao = FakeDailyContextDao()
        val repository = RoomDailyContextRepository(dao)
        repository.saveSuccessful(
            successfulContext(),
            listOf(DailyContextSource("record_1", "transcript_1", 10L, "第一条", 0)),
        )

        repository.markFailed(
            dayKey = "2026-08-27",
            zoneId = "Asia/Hong_Kong",
            failureCode = DailyContextFailureCode.PROVIDER_ERROR,
            updatedAtMillis = 200L,
        )

        val restored = repository.contexts.first().single()
        assertEquals(DailyContextStatus.FAILED, restored.status)
        assertEquals(DailyContextFailureCode.PROVIDER_ERROR, restored.failureCode)
        assertEquals("每日回顾", restored.content?.title)
        assertEquals(1, restored.sourceCount)
        assertNotNull(restored.structuredJson)
    }

    @Test
    fun `processing refresh keeps the fingerprint of the displayed successful content`() = runTest {
        val dao = FakeDailyContextDao()
        val repository = RoomDailyContextRepository(dao)
        repository.saveSuccessful(
            successfulContext(),
            listOf(DailyContextSource("record_1", "transcript_1", 10L, "第一条", 0)),
        )

        repository.markProcessing(
            dayKey = "2026-08-27",
            zoneId = "Asia/Hong_Kong",
            inputFingerprint = "replacement-fingerprint",
            updatedAtMillis = 200L,
        )

        val restored = repository.contexts.first().single()
        assertEquals("fingerprint", restored.inputFingerprint)
        assertEquals("每日回顾", restored.content?.title)
    }

    private fun successfulContext() = DailyContext(
        dayKey = "2026-08-27",
        zoneId = "Asia/Hong_Kong",
        content = DailyContextContent(
            title = "每日回顾",
            summary = "完成数据层测试",
            topics = listOf("Android"),
            ideas = emptyList(),
            questions = emptyList(),
            decisions = emptyList(),
            todos = emptyList(),
        ),
        structuredJson = COMPLETE_JSON,
        providerId = "deepseek",
        model = "deepseek-v4-flash",
        status = DailyContextStatus.SUCCEEDED,
        inputFingerprint = "fingerprint",
        sourceCount = 1,
        generatedAtMillis = 100L,
        updatedAtMillis = 100L,
    )

    private companion object {
        const val COMPLETE_JSON =
            """{"title":"每日回顾","summary":"完成数据层测试","topics":["Android"],"ideas":[],"questions":[],"decisions":[],"todos":[]}"""
        const val PARAGRAPH_JSON =
            """{"title":"每日回顾","summary":"第一段。\r\n\r\n第二段。","topics":[],"ideas":[],"questions":[],"decisions":[],"todos":[]}"""
    }
}

private class FakeDailyContextDao : DailyContextDao {
    private val state = MutableStateFlow<List<DailyContextRow>>(emptyList())
    private var entity: DailyContextEntity? = null
    var sources: List<DailyContextSourceEntity> = emptyList()

    override fun observeAll(): Flow<List<DailyContextRow>> = state

    override suspend fun findEntity(dayKey: String): DailyContextEntity? =
        entity?.takeIf { it.dayKey == dayKey }

    override suspend fun find(dayKey: String): DailyContextRow? =
        state.value.singleOrNull()?.takeIf { it.context.dayKey == dayKey }

    override suspend fun insertIfAbsent(context: DailyContextEntity): Long {
        if (entity != null) return -1L
        entity = context
        publish()
        return 1L
    }

    override suspend fun update(context: DailyContextEntity): Int {
        if (entity?.dayKey != context.dayKey) return 0
        entity = context
        publish()
        return 1
    }

    override suspend fun deleteSources(dayKey: String) {
        sources = sources.filterNot { it.dayKey == dayKey }
        publish()
    }

    override suspend fun insertSources(sources: List<DailyContextSourceEntity>) {
        this.sources = this.sources + sources
        publish()
    }

    private fun publish() {
        state.value = entity?.let { listOf(DailyContextRow(it, sources.size)) }.orEmpty()
    }
}
