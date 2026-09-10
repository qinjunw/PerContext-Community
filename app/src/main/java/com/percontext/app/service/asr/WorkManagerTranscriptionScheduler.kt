package com.percontext.app.service.asr

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.percontext.app.domain.asr.TranscriptionCanceller
import com.percontext.app.domain.asr.TranscriptionScheduler

class WorkManagerTranscriptionScheduler(
    context: Context,
) : TranscriptionScheduler, TranscriptionCanceller {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override suspend fun enqueue(recordId: String) {
        val request = OneTimeWorkRequestBuilder<LocalTranscriptionWorker>()
            .setInputData(
                Data.Builder()
                    .putString(LocalTranscriptionWorker.RECORD_ID_KEY, recordId)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            transcriptionWorkName(recordId),
            TRANSCRIPTION_WORK_POLICY,
            request,
        ).await()
    }

    override suspend fun cancel(recordId: String) {
        workManager.cancelUniqueWork(transcriptionWorkName(recordId)).await()
    }
}

internal fun transcriptionWorkName(recordId: String) = "local_transcription_$recordId"

internal val TRANSCRIPTION_WORK_POLICY = ExistingWorkPolicy.REPLACE
