package com.percontext.app.feature.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.percontext.app.PerContextApplication

class AppearanceViewModelFactory(
    application: Application,
) : ViewModelProvider.Factory {
    private val perContextApplication = application as PerContextApplication

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppearanceViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return AppearanceViewModel(perContextApplication.container.appearanceSettingsRepository) as T
    }
}
