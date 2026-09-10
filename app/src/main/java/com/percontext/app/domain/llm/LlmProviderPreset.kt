package com.percontext.app.domain.llm

data class LlmProviderPreset(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
)

object LlmProviderPresets {
    val deepSeek = LlmProviderPreset(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-v4-flash",
    )

    val custom = LlmProviderPreset(
        id = "custom",
        displayName = "自定义 OpenAI 兼容服务",
        baseUrl = "",
        defaultModel = "",
    )

    val all: List<LlmProviderPreset> = listOf(deepSeek, custom)

    fun find(id: String): LlmProviderPreset? = all.firstOrNull { it.id == id }
}
