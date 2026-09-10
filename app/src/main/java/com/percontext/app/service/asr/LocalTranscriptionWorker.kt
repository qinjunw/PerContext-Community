package com.percontext.app.service.asr

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.percontext.app.PerContextApplication
import com.percontext.app.R
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.repository.TranscriptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class LocalTranscriptionWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val recordId = inputData.getString(RECORD_ID_KEY) ?: return Result.failure()
        val application = applicationContext as? PerContextApplication ?: return Result.failure()
        val succeeded = executeTranscriptionWorker(
            recordId = recordId,
            transcriptRepository = application.container.transcriptRepository,
            enterForeground = { setForeground(createForegroundInfo()) },
            operation = { application.container.transcribeRecord(recordId) },
        )
        return if (succeeded) Result.success() else Result.failure()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val cancelWork = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(
            applicationContext,
            NOTIFICATION_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_transcribe)
            .setContentTitle(applicationContext.getString(R.string.transcription_notification_title))
            .setContentText(applicationContext.getString(R.string.transcription_notification_content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                R.drawable.ic_stop,
                applicationContext.getString(R.string.cancel_transcription),
                cancelWork,
            )
            .build()
        return ForegroundInfo(
            notificationId(),
            notification,
            transcriptionForegroundServiceType(Build.VERSION.SDK_INT),
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            applicationContext.getString(R.string.transcription_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = applicationContext.getString(R.string.transcription_channel_description)
            setSound(null, null)
        }
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun notificationId(): Int = NOTIFICATION_ID_BASE +
        (id.hashCode() and NOTIFICATION_ID_MASK)

    companion object {
        const val RECORD_ID_KEY = "record_id"

        private const val NOTIFICATION_CHANNEL_ID = "local_transcription"
        private const val NOTIFICATION_ID_BASE = 10_000
        private const val NOTIFICATION_ID_MASK = 0x0FFFFFFF
    }
}

@SuppressLint("InlinedApi")
internal fun transcriptionForegroundServiceType(sdkInt: Int): Int =
    if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
    } else {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    }

internal suspend fun executeTranscriptionWorker(
    recordId: String,
    transcriptRepository: TranscriptRepository,
    enterForeground: suspend () -> Unit,
    operation: suspend () -> Unit,
): Boolean {
    try {
        enterForeground()
    } catch (cancellation: CancellationException) {
        try {
            withContext(NonCancellable) {
                transcriptRepository.setStatus(recordId, TranscriptStatus.NOT_REQUESTED)
            }
        } catch (cleanupFailure: Throwable) {
            if (cleanupFailure !== cancellation) {
                cancellation.addSuppressed(cleanupFailure)
            }
        }
        throw cancellation
    } catch (_: Throwable) {
        withContext(NonCancellable) {
            transcriptRepository.setStatus(recordId, TranscriptStatus.FAILED)
        }
        return false
    }

    return try {
        operation()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }
}
