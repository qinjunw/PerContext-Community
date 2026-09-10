package com.percontext.app.feature.settings

import com.percontext.app.domain.llm.LlmProviderPresets
import com.percontext.app.domain.llm.LlmSettings
import com.percontext.app.feature.record.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `custom address and model are saved and changing address clears plaintext`() = runTest {
        val gateway = FakeProviderSettingsGateway()
        val viewModel = SettingsViewModel(gateway)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.selectProvider("custom")
        advanceUntilIdle()
        viewModel.updateBaseUrl("https://model.example/v1/")
        viewModel.updateModel("custom-model")
        viewModel.updateApiKey("custom-key")
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()
        assertEquals(listOf("custom" to "custom-key"), gateway.saved)
        assertEquals("https://model.example/v1", gateway.savedBaseUrl)
        assertEquals("custom-model", gateway.savedModel)
        viewModel.toggleApiKeyVisibility()
        advanceUntilIdle()
        assertEquals("custom-key", viewModel.uiState.value.apiKeyDraft)
        viewModel.updateBaseUrl("https://different.example/v1")
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.apiKeyDraft)
        assertFalse(viewModel.uiState.value.isApiKeyVisible)
        assertFalse(viewModel.uiState.value.hasSavedApiKey)
        viewModel.save()
        advanceUntilIdle()
        assertEquals(1, gateway.saved.size)
    }

    @Test
    fun `invalid custom settings are not persisted`() = runTest {
        val gateway = FakeProviderSettingsGateway()
        val viewModel = SettingsViewModel(gateway)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.selectProvider("custom")
        viewModel.updateBaseUrl("http://model.example")
        viewModel.updateModel("custom-model")
        viewModel.updateApiKey("custom-key")
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()
        assertTrue(gateway.saved.isEmpty())
    }

    @Test
    fun `saving sends only the selected preset and entered key`() = runTest {
        val gateway = FakeProviderSettingsGateway()
        val viewModel = SettingsViewModel(gateway)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.updateApiKey("user-key")
        viewModel.save()

        assertEquals(listOf("deepseek" to "user-key"), gateway.saved)
        assertTrue(viewModel.uiState.value.hasSavedApiKey)
        assertEquals("", viewModel.uiState.value.apiKeyDraft)
    }

    @Test
    fun `showing a saved key decrypts it on demand and hiding removes it from ui state`() = runTest {
        val gateway = FakeProviderSettingsGateway(savedApiKey = "saved-key")
        val viewModel = SettingsViewModel(gateway)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.toggleApiKeyVisibility()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isApiKeyVisible)
        assertEquals("saved-key", viewModel.uiState.value.apiKeyDraft)

        viewModel.toggleApiKeyVisibility()

        assertFalse(viewModel.uiState.value.isApiKeyVisible)
        assertEquals("", viewModel.uiState.value.apiKeyDraft)
    }

    @Test
    fun `leaving settings clears the revealed key without deleting the saved credential`() = runTest {
        val gateway = FakeProviderSettingsGateway(savedApiKey = "saved-key")
        val viewModel = SettingsViewModel(gateway)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        viewModel.toggleApiKeyVisibility()
        advanceUntilIdle()

        viewModel.onScreenClosed()

        assertFalse(viewModel.uiState.value.isApiKeyVisible)
        assertEquals("", viewModel.uiState.value.apiKeyDraft)
        assertTrue(viewModel.uiState.value.hasSavedApiKey)
        assertTrue(gateway.cleared.isEmpty())
        assertEquals(1, gateway.apiKeyReads)

        viewModel.toggleApiKeyVisibility()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isApiKeyVisible)
        assertEquals("saved-key", viewModel.uiState.value.apiKeyDraft)
        assertEquals(2, gateway.apiKeyReads)
    }

    @Test
    fun `key read completed after leaving settings cannot restore plaintext`() = runTest {
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val gateway = FakeProviderSettingsGateway(
            savedApiKey = "saved-key",
            beforeApiKeyReturn = {
                readStarted.complete(Unit)
                releaseRead.await()
            },
        )
        val viewModel = SettingsViewModel(gateway)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        viewModel.toggleApiKeyVisibility()
        readStarted.await()

        viewModel.onScreenClosed()
        releaseRead.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isApiKeyVisible)
        assertEquals("", viewModel.uiState.value.apiKeyDraft)
        assertEquals(1, gateway.apiKeyReads)

        viewModel.toggleApiKeyVisibility()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isApiKeyVisible)
        assertEquals("saved-key", viewModel.uiState.value.apiKeyDraft)
        assertEquals(2, gateway.apiKeyReads)
    }

    @Test
    fun `clearing a saved key removes local state and the decrypted draft`() = runTest {
        val gateway = FakeProviderSettingsGateway(savedApiKey = "saved-key")
        val viewModel = SettingsViewModel(gateway)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        viewModel.toggleApiKeyVisibility()
        advanceUntilIdle()

        viewModel.clearApiKey()
        advanceUntilIdle()

        assertEquals(listOf("deepseek"), gateway.cleared)
        assertFalse(viewModel.uiState.value.hasSavedApiKey)
        assertFalse(viewModel.uiState.value.isApiKeyVisible)
        assertEquals("", viewModel.uiState.value.apiKeyDraft)
    }
}

private class FakeProviderSettingsGateway(
    savedApiKey: String? = null,
    private val beforeApiKeyReturn: suspend () -> Unit = {},
) : ProviderSettingsGateway {
    private val state = MutableStateFlow(LlmSettings(hasApiKey = savedApiKey != null))
    private var storedKey = savedApiKey
    override val providerPresets = LlmProviderPresets.all
    override val providerSettings: Flow<LlmSettings> = state
    val saved = mutableListOf<Pair<String, String?>>()
    val cleared = mutableListOf<String>()
    var apiKeyReads = 0
    var savedBaseUrl = ""
    var savedModel = ""

    override suspend fun saveProviderSettings(providerId: String, apiKey: String?, baseUrl: String, model: String) {
        saved += providerId to apiKey
        storedKey = apiKey ?: storedKey
        savedBaseUrl = baseUrl
        savedModel = model
        state.value = LlmSettings(providerId, hasApiKey = !storedKey.isNullOrBlank(), baseUrl = baseUrl, model = model)
    }

    override suspend fun providerApiKey(providerId: String): String? {
        apiKeyReads += 1
        beforeApiKeyReturn()
        return storedKey
    }

    override suspend fun clearProviderApiKey(providerId: String) {
        cleared += providerId
        storedKey = null
        state.value = LlmSettings(providerId, hasApiKey = false)
    }
}
