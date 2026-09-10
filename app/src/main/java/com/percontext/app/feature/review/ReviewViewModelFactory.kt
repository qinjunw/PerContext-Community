package com.percontext.app.feature.review

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.percontext.app.PerContextApplication

class ReviewViewModelFactory(
    application: Application,
) : ViewModelProvider.Factory {
    private val perContextApplication = application as PerContextApplication

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ReviewViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ReviewViewModel(perContextApplication.container) as T
    }
}
