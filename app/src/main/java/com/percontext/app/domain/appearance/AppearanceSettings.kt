package com.percontext.app.domain.appearance

enum class AppTheme {
    SKY,
    SEA_SALT,
    LAVENDER,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppearanceSettings(
    val theme: AppTheme = AppTheme.SKY,
    val mode: ThemeMode = ThemeMode.SYSTEM,
)
