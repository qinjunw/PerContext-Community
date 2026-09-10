package com.percontext.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.percontext.app.domain.llm.LlmProviderPresets
import com.percontext.app.domain.llm.LlmSettings
import com.percontext.app.domain.llm.LlmSettingsRepository
import com.percontext.app.domain.llm.LlmConnection
import com.percontext.app.domain.llm.validateCustomLlmConfiguration
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.llmSettingsDataStore by preferencesDataStore(name = "llm_settings")

fun createLlmSettingsRepository(context: Context): LlmSettingsRepository =
    DataStoreLlmSettingsRepository(
        dataStore = context.applicationContext.llmSettingsDataStore,
        secretCipher = AndroidKeystoreSecretCipher(),
    )

class DataStoreLlmSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val secretCipher: SecretCipher,
) : LlmSettingsRepository {
    override val settings: Flow<LlmSettings> = dataStore.safeData().map(::toSettings)

    override suspend fun current(): LlmSettings = settings.first()

    override suspend fun connection(): LlmConnection {
        val preferences = dataStore.safeData().first()
        val settings = toSettings(preferences)
        return LlmConnection(settings, decryptApiKey(preferences, settings.providerId))
    }

    override suspend fun save(providerId: String, apiKey: String?, baseUrl: String, model: String) {
        requireNotNull(LlmProviderPresets.find(providerId)) { "Unknown LLM provider: $providerId" }
        val custom = if (providerId == LlmProviderPresets.custom.id) {
            validateCustomLlmConfiguration(baseUrl, model)
        } else null
        val trimmedKey = apiKey?.trim()?.takeIf(String::isNotEmpty)
        require(trimmedKey == null || (trimmedKey.length <= 8192 && trimmedKey.none(Char::isISOControl))) {
            "API Key 格式无效"
        }
        val encrypted = trimmedKey?.let { secretCipher.encrypt(it.toByteArray(Charsets.UTF_8)) }
        dataStore.edit { preferences ->
            if (custom != null) {
                require(preferences[CUSTOM_BASE_URL] == custom.baseUrl || encrypted != null) {
                    "更换服务地址后请输入新的 API Key"
                }
                preferences[CUSTOM_BASE_URL] = custom.baseUrl
                preferences[CUSTOM_MODEL] = custom.model
            }
            preferences[SELECTED_PROVIDER] = providerId
            if (encrypted != null) {
                preferences[ciphertextKey(providerId)] = encrypted.ciphertext.toHex()
                preferences[ivKey(providerId)] = encrypted.iv.toHex()
            }
        }
    }

    override suspend fun apiKey(providerId: String): String? {
        val preferences = dataStore.safeData().first()
        return decryptApiKey(preferences, providerId)
    }

    private fun decryptApiKey(preferences: Preferences, providerId: String): String? {
        val ciphertext = preferences[ciphertextKey(providerId)]?.hexToBytes() ?: return null
        val iv = preferences[ivKey(providerId)]?.hexToBytes() ?: return null
        return runCatching {
            secretCipher.decrypt(EncryptedSecret(ciphertext, iv)).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    override suspend fun clearApiKey(providerId: String) {
        requireNotNull(LlmProviderPresets.find(providerId)) { "Unknown LLM provider: $providerId" }
        dataStore.edit { preferences ->
            preferences.remove(ciphertextKey(providerId))
            preferences.remove(ivKey(providerId))
        }
    }

    private fun toSettings(preferences: Preferences): LlmSettings {
        val providerId = preferences[SELECTED_PROVIDER]
            ?.takeIf { LlmProviderPresets.find(it) != null }
            ?: LlmProviderPresets.deepSeek.id
        return LlmSettings(
            providerId = providerId,
            hasApiKey = preferences[ciphertextKey(providerId)] != null &&
                preferences[ivKey(providerId)] != null,
            baseUrl = preferences[CUSTOM_BASE_URL].orEmpty(),
            model = preferences[CUSTOM_MODEL].orEmpty(),
        )
    }

    private fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    private companion object {
        val SELECTED_PROVIDER = stringPreferencesKey("selected_llm_provider")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val CUSTOM_MODEL = stringPreferencesKey("custom_model")

        fun ciphertextKey(providerId: String) = stringPreferencesKey("${providerId}_api_key_ciphertext")
        fun ivKey(providerId: String) = stringPreferencesKey("${providerId}_api_key_iv")
    }
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0 || any { it.digitToIntOrNull(16) == null }) return null
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
