package com.percontext.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.percontext.app.domain.llm.LlmProviderPreset
import com.percontext.app.domain.llm.LlmProviderPresets
import com.percontext.app.domain.llm.CustomLlmConfiguration
import com.percontext.app.domain.llm.normalizeLlmBaseUrl
import com.percontext.app.domain.llm.validateCustomLlmConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val presets: List<LlmProviderPreset> = emptyList(),
    val selectedProviderId: String = "",
    val hasSavedApiKey: Boolean = false,
    val apiKeyDraft: String = "",
    val isApiKeyVisible: Boolean = false,
    val isSaving: Boolean = false,
    val baseUrlDraft: String = "",
    val modelDraft: String = "",
)

class SettingsViewModel(
    private val gateway: ProviderSettingsGateway,
) : ViewModel() {
    private val selectedProviderId = MutableStateFlow<String?>(null)
    private val apiKeyDraft = MutableStateFlow("")
    private val apiKeyVisible = MutableStateFlow(false)
    private val isSaving = MutableStateFlow(false)
    private val connectionDraft = MutableStateFlow<CustomLlmConfiguration?>(null)
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var revealedSavedKey = false
    private var revealGeneration = 0L

    val messages: Flow<String> = mutableMessages
    private val savedUiState = combine(
        gateway.providerSettings,
        selectedProviderId,
        apiKeyDraft,
        apiKeyVisible,
        isSaving,
    ) { settings, selected, draft, visible, saving ->
        SettingsUiState(
            presets = gateway.providerPresets,
            selectedProviderId = selected ?: settings.providerId,
            hasSavedApiKey = settings.hasApiKey && (selected == null || selected == settings.providerId),
            apiKeyDraft = draft,
            isApiKeyVisible = visible,
            isSaving = saving,
            baseUrlDraft = settings.baseUrl,
            modelDraft = settings.model,
        )
    }
    val uiState: StateFlow<SettingsUiState> = combine(savedUiState, connectionDraft) { state, draft ->
        if (state.selectedProviderId != LlmProviderPresets.custom.id || draft == null) state else {
            val sameEndpoint = runCatching { normalizeLlmBaseUrl(draft.baseUrl) }.getOrNull() == state.baseUrlDraft
            state.copy(
                baseUrlDraft = draft.baseUrl,
                modelDraft = draft.model,
                hasSavedApiKey = state.hasSavedApiKey && sameEndpoint,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(
            presets = gateway.providerPresets,
            selectedProviderId = gateway.providerPresets.firstOrNull()?.id.orEmpty(),
        ),
    )

    fun selectProvider(providerId: String) {
        if (gateway.providerPresets.any { it.id == providerId }) {
            revealGeneration += 1
            selectedProviderId.value = providerId
            connectionDraft.value = null
            apiKeyDraft.value = ""
            apiKeyVisible.value = false
            revealedSavedKey = false
        }
    }

    fun updateApiKey(value: String) {
        revealGeneration += 1
        revealedSavedKey = false
        apiKeyDraft.value = value
    }

    fun updateBaseUrl(value: String) {
        if (isSaving.value) return
        val state = uiState.value
        connectionDraft.value = CustomLlmConfiguration(value, connectionDraft.value?.model ?: state.modelDraft)
        revealGeneration += 1
        apiKeyDraft.value = ""
        apiKeyVisible.value = false
        revealedSavedKey = false
    }

    fun updateModel(value: String) {
        if (isSaving.value) return
        connectionDraft.value = CustomLlmConfiguration(connectionDraft.value?.baseUrl ?: uiState.value.baseUrlDraft, value)
    }

    fun onScreenClosed() {
        revealGeneration += 1
        apiKeyDraft.value = ""
        apiKeyVisible.value = false
        revealedSavedKey = false
        connectionDraft.value = null
        selectedProviderId.value = null
    }

    fun toggleApiKeyVisibility() {
        if (apiKeyVisible.value) {
            revealGeneration += 1
            apiKeyVisible.value = false
            if (revealedSavedKey) {
                apiKeyDraft.value = ""
                revealedSavedKey = false
            }
            return
        }
        val state = uiState.value
        if (state.apiKeyDraft.isNotEmpty() || !state.hasSavedApiKey) {
            apiKeyVisible.value = true
            return
        }
        val requestGeneration = ++revealGeneration
        viewModelScope.launch {
            try {
                val savedKey = gateway.providerApiKey(state.selectedProviderId)
                if (requestGeneration != revealGeneration) return@launch
                if (savedKey.isNullOrEmpty()) {
                    mutableMessages.emit("未找到已保存的 API Key")
                    return@launch
                }
                apiKeyDraft.value = savedKey
                revealedSavedKey = true
                apiKeyVisible.value = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (requestGeneration == revealGeneration) {
                    mutableMessages.emit("读取 API Key 失败")
                }
            }
        }
    }

    fun clearApiKey() {
        val state = uiState.value
        if (!state.hasSavedApiKey) return
        revealGeneration += 1
        viewModelScope.launch {
            isSaving.value = true
            try {
                gateway.clearProviderApiKey(state.selectedProviderId)
                apiKeyDraft.value = ""
                apiKeyVisible.value = false
                revealedSavedKey = false
                mutableMessages.emit("本地 API Key 已清除")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                mutableMessages.emit("清除 API Key 失败")
            } finally {
                isSaving.value = false
            }
        }
    }

    fun save() {
        if (isSaving.value) return
        val state = uiState.value
        val custom = if (state.selectedProviderId == LlmProviderPresets.custom.id) {
            try {
                validateCustomLlmConfiguration(state.baseUrlDraft, state.modelDraft)
            } catch (error: IllegalArgumentException) {
                mutableMessages.tryEmit(error.message ?: "请检查模型服务配置")
                return
            }
        } else null
        if (state.apiKeyDraft.isBlank() && !state.hasSavedApiKey) {
            mutableMessages.tryEmit("请输入 API Key")
            return
        }
        revealGeneration += 1
        viewModelScope.launch {
            isSaving.value = true
            try {
                gateway.saveProviderSettings(
                    providerId = state.selectedProviderId,
                    apiKey = state.apiKeyDraft.takeIf(String::isNotBlank),
                    baseUrl = custom?.baseUrl.orEmpty(),
                    model = custom?.model.orEmpty(),
                )
                apiKeyDraft.value = ""
                apiKeyVisible.value = false
                revealedSavedKey = false
                selectedProviderId.value = null
                connectionDraft.value = null
                mutableMessages.emit("模型服务设置已保存")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                mutableMessages.emit("设置保存失败")
            } finally {
                isSaving.value = false
            }
        }
    }
}
