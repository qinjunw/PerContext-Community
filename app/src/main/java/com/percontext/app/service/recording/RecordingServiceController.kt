package com.percontext.app.service.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class RecordingServiceController(context: Context) {
    private val applicationContext = context.applicationContext

    fun start() {
        val intent = Intent(applicationContext, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
        ContextCompat.startForegroundService(applicationContext, intent)
    }

    fun stop() {
        val intent = Intent(applicationContext, RecordingService::class.java)
            .setAction(RecordingService.ACTION_STOP)
        applicationContext.startService(intent)
    }
}
