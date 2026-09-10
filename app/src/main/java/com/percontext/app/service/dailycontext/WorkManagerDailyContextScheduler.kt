package com.percontext.app.service.dailycontext

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.percontext.app.domain.dailycontext.DailyContextScheduler

class WorkManagerDailyContextScheduler(
    context: Context,
) : DailyContextScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override suspend fun enqueue(dayKey: String, zoneId: String) {
        val request = OneTimeWorkRequestBuilder<DailyContextWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                Data.Builder()
                    .putString(DailyContextWorker.DAY_KEY, dayKey)
                    .putString(DailyContextWorker.ZONE_ID_KEY, zoneId)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            dailyContextWorkName(dayKey),
            DAILY_CONTEXT_WORK_POLICY,
            request,
        ).await()
    }
}

internal fun dailyContextWorkName(dayKey: String) = "daily_context_$dayKey"

internal val DAILY_CONTEXT_WORK_POLICY = ExistingWorkPolicy.KEEP
