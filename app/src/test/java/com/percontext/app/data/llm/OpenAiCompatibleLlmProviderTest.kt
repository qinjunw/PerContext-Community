package com.percontext.app.data.llm

import com.percontext.app.domain.llm.LlmMessage
import com.percontext.app.domain.llm.LlmMessageRole
import com.percontext.app.domain.llm.LlmProviderException
import com.percontext.app.domain.llm.LlmRequest
import com.percontext.app.domain.llm.LlmSettings
import com.percontext.app.domain.llm.LlmSettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleLlmProviderTest {
    @Test
    fun `custom service uses the configured endpoint model and key without vendor extensions`() = runTest {
        val repository = object : LlmSettingsRepository by FixedSettingsRepository {
            override suspend fun current() = LlmSettings("custom", true, "https://model.example/v1/chat/completions", "custom-model")
            override suspend fun connection() = com.percontext.app.domain.llm.LlmConnection(current(), "local-test-key")
        }
        val client = createLlmHttpClient(MockEngine { request ->
            assertEquals("https://model.example/v1/chat/completions", request.url.toString())
            assertEquals("Bearer local-test-key", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("custom-model", body.getValue("model").jsonPrimitive.content)
            assertFalse(body.containsKey("thinking"))
            assertFalse(body.containsKey("reasoning_effort"))
            respond(SUCCESS_RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val response = OpenAiCompatibleLlmProvider(client, repository).generate(
                LlmRequest(listOf(LlmMessage(LlmMessageRole.USER, "json"))))
            assertEquals("custom", response.providerId)
        } finally { client.close() }
    }

    @Test
    fun `redirects are rejected before credentials can reach another endpoint`() = runTest {
        val visited = mutableListOf<String>()
        val client = createLlmHttpClient(MockEngine { request ->
            visited += request.url.toString()
            respond("", HttpStatusCode.TemporaryRedirect,
                headersOf(HttpHeaders.Location, "https://different.example/collect"))
        })
        try {
            val failure = runCatching {
                OpenAiCompatibleLlmProvider(client, FixedSettingsRepository).generate(
                    LlmRequest(listOf(LlmMessage(LlmMessageRole.USER, "json"))))
            }.exceptionOrNull()
            assertTrue(failure is LlmProviderException)
            assertEquals(listOf("https://api.deepseek.com/chat/completions"), visited)
        } finally { client.close() }
    }

    @Test
    fun `deepseek preset supplies endpoint model and bearer key`() = runTest {
        var observedUrl = ""
        var observedAuthorization = ""
        var observedBody = ""
        val engine = MockEngine { request ->
            observedUrl = request.url.toString()
            observedAuthorization = request.headers[HttpHeaders.Authorization].orEmpty()
            observedBody = (request.body as TextContent).text
            respond(
                content = SUCCESS_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val provider = OpenAiCompatibleLlmProvider(
            httpClient = HttpClient(engine),
            settingsRepository = FixedSettingsRepository,
        )

        val response = provider.generate(
            LlmRequest(
                messages = listOf(LlmMessage(LlmMessageRole.USER, "output json")),
            ),
        )

        val body = Json.parseToJsonElement(observedBody).jsonObject
        assertEquals("https://api.deepseek.com/chat/completions", observedUrl)
        assertEquals("Bearer local-test-key", observedAuthorization)
        assertEquals("deepseek-v4-flash", body.getValue("model").jsonPrimitive.content)
        assertEquals("json_object", body.getValue("response_format").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("enabled", body.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("low", body.getValue("reasoning_effort").jsonPrimitive.content)
        assertFalse(body.containsKey("max_tokens"))
        assertEquals("deepseek", response.providerId)
        assertEquals("当天回顾", Json.parseToJsonElement(response.content).jsonObject.getValue("title").jsonPrimitive.content)
    }

    @Test
    fun `empty provider content is an explicit failure`() = runTest {
        val provider = OpenAiCompatibleLlmProvider(
            httpClient = HttpClient(MockEngine {
                respond(
                    content = """{"model":"deepseek-v4-flash","choices":[{"finish_reason":"stop","message":{"content":""}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }),
            settingsRepository = FixedSettingsRepository,
        )

        val failure = runCatching {
            provider.generate(LlmRequest(listOf(LlmMessage(LlmMessageRole.USER, "json"))))
        }.exceptionOrNull()

        assertTrue(failure is LlmProviderException)
    }

    @Test
    fun `non stop finish reason is included in provider failure`() = runTest {
        val provider = OpenAiCompatibleLlmProvider(
            httpClient = HttpClient(MockEngine {
                respond(
                    content = """{"model":"deepseek-v4-flash","choices":[{"finish_reason":"length","message":{"content":""}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }),
            settingsRepository = FixedSettingsRepository,
        )

        val failure = runCatching {
            provider.generate(LlmRequest(listOf(LlmMessage(LlmMessageRole.USER, "json"))))
        }.exceptionOrNull()

        assertEquals("LLM response finish_reason=length", failure?.message)
    }

    private companion object {
        const val SUCCESS_RESPONSE =
            """{"model":"deepseek-v4-flash","choices":[{"finish_reason":"stop","message":{"content":"{\"title\":\"当天回顾\"}"}}]}"""
    }
}

private object FixedSettingsRepository : LlmSettingsRepository {
    override val settings: Flow<LlmSettings> = flowOf(LlmSettings(hasApiKey = true))

    override suspend fun current(): LlmSettings = LlmSettings(hasApiKey = true)

    override suspend fun save(providerId: String, apiKey: String?, baseUrl: String, model: String) = Unit

    override suspend fun apiKey(providerId: String): String = "local-test-key"

    override suspend fun clearApiKey(providerId: String) = Unit
}
