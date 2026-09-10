package com.percontext.app.domain.llm

import java.net.URI

data class CustomLlmConfiguration(val baseUrl: String, val model: String)

fun normalizeLlmBaseUrl(value: String): String {
    val uri = runCatching { URI(value.trim()) }.getOrNull()
    require(uri != null && uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawQuery == null &&
        uri.rawFragment == null && (uri.port == -1 || uri.port in 1..65535)) {
        "请输入有效的 HTTPS API 地址，地址中不能包含账号、查询参数或片段"
    }
    val path = uri.rawPath.orEmpty().trimEnd('/').removeSuffix("/chat/completions")
    val host = uri.host.lowercase()
    val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    return "https://$host$port$path"
}

fun validateCustomLlmConfiguration(baseUrl: String, model: String): CustomLlmConfiguration {
    val normalizedModel = model.trim()
    require(normalizedModel.isNotEmpty() && normalizedModel.length <= 200 &&
        normalizedModel.none(Char::isISOControl)) { "请输入有效的模型名称" }
    return CustomLlmConfiguration(normalizeLlmBaseUrl(baseUrl), normalizedModel)
}
