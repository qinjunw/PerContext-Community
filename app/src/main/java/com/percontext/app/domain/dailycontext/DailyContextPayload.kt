package com.percontext.app.domain.dailycontext

import com.percontext.app.domain.llm.LlmMessage
import com.percontext.app.domain.llm.LlmMessageRole
import com.percontext.app.domain.llm.LlmRequest
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private const val DAILY_CONTEXT_INPUT_SCHEMA_VERSION = "daily_context_input.v1"

class DailyContextPayloadBuilder(
    private val json: Json = Json,
) {
    fun build(
        dayKey: String,
        zoneId: String,
        sources: List<DailyContextSource>,
    ): LlmRequest {
        require(sources.isNotEmpty()) { "Daily context requires at least one transcript" }
        val input = buildJsonObject {
            put("schema_version", DAILY_CONTEXT_INPUT_SCHEMA_VERSION)
            put("day", dayKey)
            put("timezone", zoneId)
            put("entries", buildJsonArray {
                sources.forEachIndexed { index, source ->
                    add(buildJsonObject {
                        put("source_ref", "R${index + 1}")
                        put("recorded_at", clockTimeAt(source.recordedAtMillis, zoneId))
                        put("text", source.text)
                    })
                }
            })
        }
        return LlmRequest(
            messages = listOf(
                LlmMessage(LlmMessageRole.SYSTEM, SYSTEM_PROMPT),
                LlmMessage(
                    LlmMessageRole.USER,
                    "请只根据以下输入生成 json：\n" +
                        json.encodeToString(JsonElement.serializer(), input),
                ),
            ),
        )
    }

    private companion object {
        val SYSTEM_PROMPT = """
            你是用户个人思考的整理者，不是会议纪要工具或评价者。
            只使用用户提供的转写内容，不补充、猜测或创造未出现的信息。
            整理时依次遵守以下原则：
            1. 准确保留用户表达的事实、术语、观点和确定程度。
            2. 梳理内容之间的关系，让用户以后能快速找回当时的思路。
            3. 使用自然、克制、有人情味的中文，不添加鼓励、评价、情绪或用户未表达的动机。
            字段要求：
            - title：使用简短、自然的概念性标题，避免添加“记录”“回顾”“总结”等通用后缀。
            - summary：直接概括核心内容和思考脉络，避免以“记录了”开头；按语义转折整理为 1 至 4 个自然段，每段只承担一个中心意思，内容较短时保持一段；段落之间在 JSON 字符串中使用 \n\n 分隔，不添加小标题。
            - topics：提取少量便于回看和检索的主题标签。
            - ideas：只提取用户明确表达的想法、假设或洞见，不把普通事实包装成个人洞见。
            - questions：只提取用户明确提出或尚未解决的问题。
            - decisions：只提取用户已经明确确定的决定，不把设想升级为决定。
            - todos：只提取用户明确需要继续执行的事项，不把建议升级为待办。
            保留“可能”“倾向”“尚未确定”“需要验证”等不确定性。
            没有对应内容时返回空数组，不为了填满字段而推测内容。
            输出必须是一个 json 对象，不要输出 Markdown 或解释文字。
            输出格式：
            {"title":"简短标题","summary":"当天摘要","topics":[],"ideas":[],"questions":[],"decisions":[],"todos":[]}
            所有数组元素必须是字符串；没有内容时返回空数组。
        """.trimIndent()
    }
}

class DailyContextResponseParser(
    private val json: Json = Json,
) {
    fun parse(rawContent: String): ParsedDailyContext {
        val root = runCatching { json.parseToJsonElement(rawContent).jsonObject }
            .getOrElse { throw InvalidDailyContextResponseException() }
        val content = DailyContextContent(
            title = root.requiredString("title"),
            summary = root.requiredString("summary").normalizeParagraphBreaks(),
            topics = root.requiredStringArray("topics"),
            ideas = root.requiredStringArray("ideas"),
            questions = root.requiredStringArray("questions"),
            decisions = root.requiredStringArray("decisions"),
            todos = root.requiredStringArray("todos"),
        )
        val normalized = buildJsonObject {
            put("title", content.title)
            put("summary", content.summary)
            put("topics", content.topics.toJsonArray())
            put("ideas", content.ideas.toJsonArray())
            put("questions", content.questions.toJsonArray())
            put("decisions", content.decisions.toJsonArray())
            put("todos", content.todos.toJsonArray())
        }
        return ParsedDailyContext(
            content = content,
            structuredJson = json.encodeToString(JsonElement.serializer(), normalized),
        )
    }

    private fun JsonObject.requiredString(name: String): String {
        val primitive = this[name] as? JsonPrimitive
            ?: throw InvalidDailyContextResponseException()
        if (!primitive.isString || primitive.content.isBlank()) {
            throw InvalidDailyContextResponseException()
        }
        return primitive.content.trim()
    }

    private fun JsonObject.requiredStringArray(name: String): List<String> {
        val array = this[name] as? JsonArray ?: throw InvalidDailyContextResponseException()
        return array.map { element ->
            val primitive = element as? JsonPrimitive
                ?: throw InvalidDailyContextResponseException()
            if (!primitive.isString || primitive.content.isBlank()) {
                throw InvalidDailyContextResponseException()
            }
            primitive.content.trim()
        }
    }

    private fun List<String>.toJsonArray() = JsonArray(map(::JsonPrimitive))

    private fun String.normalizeParagraphBreaks(): String =
        replace("\r\n", "\n")
            .replace('\r', '\n')
            .split(Regex("(?:\\n[\\t ]*){2,}"))
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n\n")
}

data class ParsedDailyContext(
    val content: DailyContextContent,
    val structuredJson: String,
)

class InvalidDailyContextResponseException : IllegalArgumentException("Invalid daily context JSON")

fun dailyContextInputFingerprint(sources: List<DailyContextSource>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    sources.forEach { source ->
        listOf(
            source.recordId,
            source.transcriptId,
            source.recordedAtMillis.toString(),
            source.text,
        ).forEach { part ->
            digest.update(part.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
