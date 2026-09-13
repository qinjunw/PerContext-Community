package com.percontext.app.feature.settings

import com.percontext.app.domain.appearance.AppTheme
import com.percontext.app.domain.appearance.AppearanceSettings
import com.percontext.app.domain.appearance.AppearanceSettingsRepository
import com.percontext.app.domain.appearance.ThemeMode
import com.percontext.app.feature.record.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `settings stay unloaded until first value and collect without a ui subscriber`() = runTest {
        val source = MutableSharedFlow<AppearanceSettings>()
        val repository = FakeAppearanceSettingsRepository(settingsSource = source)
        val viewModel = AppearanceViewModel(repository)
        assertNull(viewModel.settings.value)

        source.emit(AppearanceSettings(AppTheme.LAVENDER, ThemeMode.DARK))
        advanceUntilIdle()

        assertEquals(AppearanceSettings(AppTheme.LAVENDER, ThemeMode.DARK), viewModel.settings.value)
    }

    @Test
    fun `selection applies after save succeeds and theme and mode save independently`() = runTest {
        val allowSave = CompletableDeferred<Unit>()
        val repository = FakeAppearanceSettingsRepository(beforeSave = { allowSave.await() })
        val viewModel = AppearanceViewModel(repository)

        viewModel.selectTheme(AppTheme.SEA_SALT)
        viewModel.selectMode(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(AppearanceSettings(), viewModel.settings.value)

        allowSave.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(AppTheme.SEA_SALT), repository.savedThemes)
        assertEquals(listOf(ThemeMode.DARK), repository.savedModes)
        assertEquals(AppearanceSettings(AppTheme.SEA_SALT, ThemeMode.DARK), viewModel.settings.value)
    }

    @Test
    fun `failed selection retains saved appearance and reports failure`() = runTest {
        val original = AppearanceSettings(AppTheme.LAVENDER, ThemeMode.DARK)
        val repository = FakeAppearanceSettingsRepository(initial = original, beforeSave = { throw IOException("disk") })
        val viewModel = AppearanceViewModel(repository)
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.collect(messages::add)
        }

        viewModel.selectTheme(AppTheme.SKY)
        viewModel.selectMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(original, viewModel.settings.value)
        assertEquals(listOf("外观设置保存失败，请重试", "外观设置保存失败，请重试"), messages)
        assertTrue(repository.savedThemes.isEmpty())
        assertTrue(repository.savedModes.isEmpty())
    }

    @Test
    fun `cancelled save is not reported as a failure and leaves the saved appearance unchanged`() = runTest {
        var cancelSave = true
        val repository = FakeAppearanceSettingsRepository(beforeSave = {
            if (cancelSave) throw CancellationException("closed")
        })
        val viewModel = AppearanceViewModel(repository)
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.collect(messages::add)
        }

        viewModel.selectTheme(AppTheme.SEA_SALT)
        advanceUntilIdle()

        assertEquals(AppearanceSettings(), viewModel.settings.value)
        assertTrue(messages.isEmpty())
        assertTrue(repository.savedThemes.isEmpty())
        cancelSave = false
        viewModel.selectTheme(AppTheme.LAVENDER)
        advanceUntilIdle()
        assertEquals(AppTheme.LAVENDER, viewModel.settings.value?.theme)
    }
}

private class FakeAppearanceSettingsRepository(
    initial: AppearanceSettings = AppearanceSettings(),
    settingsSource: Flow<AppearanceSettings>? = null,
    private val beforeSave: suspend () -> Unit = {},
) : AppearanceSettingsRepository {
    private val storedSettings = MutableStateFlow(initial)
    override val settings = settingsSource ?: storedSettings
    val savedThemes = mutableListOf<AppTheme>()
    val savedModes = mutableListOf<ThemeMode>()

    override suspend fun setTheme(theme: AppTheme) {
        beforeSave()
        savedThemes += theme
        storedSettings.value = storedSettings.value.copy(theme = theme)
    }

    override suspend fun setMode(mode: ThemeMode) {
        beforeSave()
        savedModes += mode
        storedSettings.value = storedSettings.value.copy(mode = mode)
    }
}
