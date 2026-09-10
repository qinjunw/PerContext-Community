package com.percontext.app.domain.llm

fun interface LlmProvider {
    suspend fun generate(request: LlmRequest): LlmResponse
}

data class LlmRequest(
    val messages: List<LlmMessage>,
    val responseFormat: LlmResponseFormat = LlmResponseFormat.JSON_OBJECT,
)

data class LlmMessage(
    val role: LlmMessageRole,
    val content: String,
)

enum class LlmMessageRole(val wireValue: String) {
    SYSTEM("system"),
    USER("user"),
}

enum class LlmResponseFormat {
    JSON_OBJECT,
}

data class LlmResponse(
    val content: String,
    val providerId: String,
    val model: String,
)

class LlmConfigurationException(message: String) : IllegalStateException(message)

class LlmProviderException(message: String) : IllegalStateException(message)
