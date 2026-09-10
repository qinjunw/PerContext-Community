package com.percontext.app.data.llm

import com.percontext.app.domain.llm.LlmConfigurationException
import com.percontext.app.domain.llm.LlmProvider
import com.percontext.app.domain.llm.LlmProviderException
import com.percontext.app.domain.llm.LlmProviderPresets
import com.percontext.app.domain.llm.LlmRequest
import com.percontext.app.domain.llm.LlmResponse
import com.percontext.app.domain.llm.LlmSettingsRepository
import com.percontext.app.domain.llm.CustomLlmConfiguration
import com.percontext.app.domain.llm.validateCustomLlmConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun createLlmHttpClient(
    engine: HttpClientEngine = OkHttp.create {
        config {
            followRedirects(false)
            followSslRedirects(false)
        }
    },
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000L
        requestTimeoutMillis = 120_000L
        socketTimeoutMillis = 120_000L
    }
}

class OpenAiCompatibleLlmProvider(
    private val httpClient: HttpClient,
    private val settingsRepository: LlmSettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LlmProvider {
    override suspend fun generate(request: LlmRequest): LlmResponse {
        val connection = settingsRepository.connection()
        val settings = connection.settings
        val preset = LlmProviderPresets.find(settings.providerId)
            ?: throw LlmConfigurationException("Unknown LLM provider")
        val configuration = if (preset.id == LlmProviderPresets.custom.id) {
            runCatching { validateCustomLlmConfiguration(settings.baseUrl, settings.model) }
                .getOrElse { throw LlmConfigurationException("Invalid custom model configuration") }
        } else CustomLlmConfiguration(preset.baseUrl, preset.defaultModel)
        val apiKey = connection.apiKey?.takeIf(String::isNotBlank)
            ?: throw LlmConfigurationException("Missing API key")
        val requestBody = buildJsonObject {
            put("model", configuration.model)
            put("messages", buildJsonArray {
                request.messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.role.wireValue)
                        put("content", message.content)
                    })
                }
            })
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("stream", false)
            if (preset.id == LlmProviderPresets.deepSeek.id) {
                put("thinking", buildJsonObject { put("type", "enabled") })
                put("reasoning_effort", "low")
            }
        }
        val response = httpClient.post("${configuration.baseUrl.trimEnd('/')}/chat/completions") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonElement.serializer(), requestBody))
        }
        if (!response.status.isSuccess()) {
            throw LlmProviderException("LLM request failed with HTTP ${response.status.value}")
        }
        val root = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }
            .getOrElse { throw LlmProviderException("LLM response is not valid JSON") }
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw LlmProviderException("LLM response has no choices")
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.content.orEmpty()
        if (finishReason != "stop") {
            throw LlmProviderException("LLM response finish_reason=${finishReason.ifBlank { "missing" }}")
        }
        val content = choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?.takeIf(String::isNotBlank)
            ?: throw LlmProviderException("LLM response content is empty")
        val model = root["model"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: configuration.model
        return LlmResponse(
            content = content,
            providerId = preset.id,
            model = model,
        )
    }
}
