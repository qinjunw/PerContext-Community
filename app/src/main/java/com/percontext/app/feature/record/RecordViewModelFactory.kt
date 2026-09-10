package com.percontext.app.feature.record

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.percontext.app.PerContextApplication
import com.percontext.app.core.media.AudioPlaybackController

class RecordViewModelFactory(
    application: Application,
) : ViewModelProvider.Factory {
    private val perContextApplication = application as PerContextApplication

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RecordViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return RecordViewModel(
            gateway = perContextApplication.container,
            playbackController = AudioPlaybackController(perContextApplication),
        ) as T
    }
}
