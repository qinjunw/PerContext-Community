package com.percontext.app.domain.dailycontext

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyContextPayloadTest {
    @Test
    fun `payload keeps every transcript in recording order`() {
        val sources = listOf(
            source("record_1", "transcript_1", 1_787_766_120_000L, "第一个想法"),
            source("record_2", "transcript_2", 1_787_769_720_000L, "第二个想法"),
        )

        val request = DailyContextPayloadBuilder().build(
            dayKey = "2026-08-27",
            zoneId = "Asia/Hong_Kong",
            sources = sources,
        )

        val userJson = request.messages.last().content.substringAfter("：\n")
        val input = Json.parseToJsonElement(userJson).jsonObject
        val entries = input.getValue("entries").jsonArray
        assertEquals("daily_context_input.v1", input.getValue("schema_version").jsonPrimitive.content)
        assertEquals(listOf("R1", "R2"), entries.map { it.jsonObject.getValue("source_ref").jsonPrimitive.content })
        assertEquals(listOf("第一个想法", "第二个想法"), entries.map { it.jsonObject.getValue("text").jsonPrimitive.content })
        assertTrue(request.messages.first().content.contains("json"))
    }

    @Test
    fun `system prompt preserves personal voice and the user's level of certainty`() {
        val request = DailyContextPayloadBuilder().build(
            dayKey = "2026-08-28",
            zoneId = "Asia/Hong_Kong",
            sources = listOf(source("record_1", "transcript_1", 10L, "我可能会先试试这个方案")),
        )

        val prompt = request.messages.first().content
        assertTrue(prompt.contains("个人思考的整理者"))
        assertTrue(prompt.contains("确定程度"))
        assertTrue(prompt.contains("自然、克制"))
        assertTrue(prompt.contains("1 至 4 个自然段"))
        assertTrue(prompt.contains("\\n\\n"))
        assertTrue(prompt.contains("不添加小标题"))
        assertTrue(prompt.contains("不把设想升级为决定"))
        assertTrue(prompt.contains("不为了填满字段"))
    }

    @Test
    fun `parser normalizes a complete daily context`() {
        val parsed = DailyContextResponseParser().parse(
            """{"title":"今天","summary":"完成测试","topics":["安卓"],"ideas":[],"questions":[],"decisions":["先做 MVP"],"todos":[]}""",
        )

        assertEquals("今天", parsed.content.title)
        assertEquals(listOf("先做 MVP"), parsed.content.decisions)
        assertEquals("完成测试", Json.parseToJsonElement(parsed.structuredJson).jsonObject.getValue("summary").jsonPrimitive.content)
    }

    @Test
    fun `parser normalizes model paragraph separators without inventing paragraphs`() {
        val parsed = DailyContextResponseParser().parse(
            """{"title":"今天","summary":"第一句。\n第二句。\r\n \r\n 第二段。\n\n\n第三段。","topics":[],"ideas":[],"questions":[],"decisions":[],"todos":[]}""",
        )

        assertEquals("第一句。\n第二句。\n\n第二段。\n\n第三段。", parsed.content.summary)
        assertEquals(
            "第一句。\n第二句。\n\n第二段。\n\n第三段。",
            Json.parseToJsonElement(parsed.structuredJson).jsonObject.getValue("summary").jsonPrimitive.content,
        )
    }

    @Test(expected = InvalidDailyContextResponseException::class)
    fun `parser rejects markdown around json`() {
        DailyContextResponseParser().parse(
            """```json
            {"title":"今天"}
            ```""",
        )
    }

    @Test
    fun `fingerprint changes when transcript text changes`() {
        val original = listOf(source("r1", "t1", 10L, "原文"))
        val changed = listOf(source("r1", "t1", 10L, "修改"))

        assertNotEquals(
            dailyContextInputFingerprint(original),
            dailyContextInputFingerprint(changed),
        )
    }

    @Test
    fun `local day range follows daylight saving boundary`() {
        val range = localDayRange("2026-03-08", "America/New_York")

        assertEquals(23L * 60L * 60L * 1_000L, range.endExclusiveMillis - range.startMillis)
    }

    private fun source(recordId: String, transcriptId: String, time: Long, text: String) =
        DailyContextSource(recordId, transcriptId, time, text)
}
