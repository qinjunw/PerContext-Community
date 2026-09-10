package com.percontext.app.feature.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.percontext.app.PerContextApplication

class SettingsViewModelFactory(
    application: Application,
) : ViewModelProvider.Factory {
    private val perContextApplication = application as PerContextApplication

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(perContextApplication.container) as T
    }
}
