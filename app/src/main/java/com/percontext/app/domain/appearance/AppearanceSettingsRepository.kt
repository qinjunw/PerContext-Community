package com.percontext.app.domain.appearance

import kotlinx.coroutines.flow.Flow

interface AppearanceSettingsRepository {
    val settings: Flow<AppearanceSettings>

    suspend fun setTheme(theme: AppTheme)

    suspend fun setMode(mode: ThemeMode)
}
