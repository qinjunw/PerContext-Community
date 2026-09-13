package com.percontext.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.percontext.app.domain.appearance.AppTheme
import com.percontext.app.domain.appearance.AppearanceSettings
import com.percontext.app.domain.appearance.AppearanceSettingsRepository
import com.percontext.app.domain.appearance.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appearanceSettingsDataStore by preferencesDataStore(name = "appearance_settings")

fun createAppearanceSettingsRepository(context: Context): AppearanceSettingsRepository =
    DataStoreAppearanceSettingsRepository(context.applicationContext.appearanceSettingsDataStore)

class DataStoreAppearanceSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AppearanceSettingsRepository {
    override val settings: Flow<AppearanceSettings> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        AppearanceSettings(
            theme = when (preferences[SELECTED_THEME]) {
                "sea_salt" -> AppTheme.SEA_SALT
                "lavender" -> AppTheme.LAVENDER
                else -> AppTheme.SKY
            },
            mode = when (preferences[SELECTED_MODE]) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            },
        )
    }

    override suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[SELECTED_THEME] = when (theme) {
                AppTheme.SKY -> "sky"
                AppTheme.SEA_SALT -> "sea_salt"
                AppTheme.LAVENDER -> "lavender"
            }
        }
    }

    override suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[SELECTED_MODE] = when (mode) {
                ThemeMode.SYSTEM -> "system"
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
            }
        }
    }

    private companion object {
        val SELECTED_THEME = stringPreferencesKey("selected_theme")
        val SELECTED_MODE = stringPreferencesKey("selected_mode")
    }
}
