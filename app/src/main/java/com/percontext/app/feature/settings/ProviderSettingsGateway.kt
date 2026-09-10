package com.percontext.app.feature.settings

import com.percontext.app.domain.llm.LlmProviderPreset
import com.percontext.app.domain.llm.LlmSettings
import kotlinx.coroutines.flow.Flow

interface ProviderSettingsGateway {
    val providerPresets: List<LlmProviderPreset>
    val providerSettings: Flow<LlmSettings>

    suspend fun saveProviderSettings(providerId: String, apiKey: String?, baseUrl: String = "", model: String = "")

    suspend fun providerApiKey(providerId: String): String?

    suspend fun clearProviderApiKey(providerId: String)
}
