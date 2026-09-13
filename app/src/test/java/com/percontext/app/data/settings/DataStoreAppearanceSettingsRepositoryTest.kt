package com.percontext.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.percontext.app.domain.appearance.AppTheme
import com.percontext.app.domain.appearance.AppearanceSettings
import com.percontext.app.domain.appearance.ThemeMode
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreAppearanceSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `new installation uses sky theme and system mode`() = runTest {
        val repository = DataStoreAppearanceSettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(temporaryFolder.root, "defaults.preferences_pb") },
            ),
        )

        assertEquals(AppearanceSettings(), repository.settings.first())
    }

    @Test
    fun `stable values survive closing and reopening the settings file`() = runTest {
        val settingsFile = File(temporaryFolder.root, "persisted.preferences_pb")
        val firstJob = SupervisorJob(backgroundScope.coroutineContext[Job])
        val firstStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(backgroundScope.coroutineContext + firstJob),
            produceFile = { settingsFile },
        )
        val repository = DataStoreAppearanceSettingsRepository(firstStore)
        repository.setTheme(AppTheme.SEA_SALT)
        repository.setMode(ThemeMode.DARK)
        assertEquals("sea_salt", firstStore.data.first()[stringPreferencesKey("selected_theme")])
        assertEquals("dark", firstStore.data.first()[stringPreferencesKey("selected_mode")])
        firstJob.cancelAndJoin()

        val reopened = DataStoreAppearanceSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { settingsFile }),
        )
        assertEquals(AppearanceSettings(AppTheme.SEA_SALT, ThemeMode.DARK), reopened.settings.first())
    }

    @Test
    fun `unrecognized preference values fall back independently`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "unknown.preferences_pb") },
        )
        val repository = DataStoreAppearanceSettingsRepository(store)
        store.edit {
            it[stringPreferencesKey("selected_theme")] = "future_theme"
            it[stringPreferencesKey("selected_mode")] = "dark"
        }
        assertEquals(AppearanceSettings(AppTheme.SKY, ThemeMode.DARK), repository.settings.first())

        store.edit {
            it[stringPreferencesKey("selected_theme")] = "lavender"
            it[stringPreferencesKey("selected_mode")] = "future_mode"
        }
        assertEquals(AppearanceSettings(AppTheme.LAVENDER, ThemeMode.SYSTEM), repository.settings.first())
    }

    @Test
    fun `concurrent theme and mode changes preserve each other and unrelated settings`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "updates.preferences_pb") },
        )
        val unrelatedKey = stringPreferencesKey("future_setting")
        store.edit { it[unrelatedKey] = "keep" }
        val repository = DataStoreAppearanceSettingsRepository(store)

        val themeWrite = async { repository.setTheme(AppTheme.LAVENDER) }
        val modeWrite = async { repository.setMode(ThemeMode.LIGHT) }
        themeWrite.await()
        modeWrite.await()

        assertEquals(AppearanceSettings(AppTheme.LAVENDER, ThemeMode.LIGHT), repository.settings.first())
        assertEquals("keep", store.data.first()[unrelatedKey])
        repository.setTheme(AppTheme.SKY)
        assertEquals(AppearanceSettings(AppTheme.SKY, ThemeMode.LIGHT), repository.settings.first())
        repository.setMode(ThemeMode.SYSTEM)
        assertEquals(AppearanceSettings(), repository.settings.first())
    }

    @Test
    fun `unreadable preferences use defaults while write failures propagate`() = runTest {
        val repository = DataStoreAppearanceSettingsRepository(FailingAppearanceDataStore(IOException("disk")))

        assertEquals(AppearanceSettings(), repository.settings.first())
        assertTrue(runCatching { repository.setTheme(AppTheme.LAVENDER) }.exceptionOrNull() is IOException)
    }

    @Test
    fun `unexpected read errors propagate`() = runTest {
        val failure = IllegalStateException("unexpected")
        val repository = DataStoreAppearanceSettingsRepository(FailingAppearanceDataStore(failure))

        assertEquals(failure, runCatching { repository.settings.first() }.exceptionOrNull())
    }
}

private class FailingAppearanceDataStore(private val failure: Exception) : DataStore<Preferences> {
    override val data = flow<Preferences> { throw failure }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        throw failure
}
