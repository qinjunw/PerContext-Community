package com.percontext.app.domain.llm

import kotlinx.coroutines.flow.Flow

data class LlmSettings(
    val providerId: String = LlmProviderPresets.deepSeek.id,
    val hasApiKey: Boolean = false,
    val baseUrl: String = "",
    val model: String = "",
)

class LlmConnection(val settings: LlmSettings, val apiKey: String?)

interface LlmSettingsRepository {
    val settings: Flow<LlmSettings>

    suspend fun current(): LlmSettings

    suspend fun connection(): LlmConnection {
        val settings = current()
        return LlmConnection(settings, apiKey(settings.providerId))
    }

    suspend fun save(providerId: String, apiKey: String?, baseUrl: String = "", model: String = "")

    suspend fun apiKey(providerId: String): String?

    suspend fun clearApiKey(providerId: String)
}
