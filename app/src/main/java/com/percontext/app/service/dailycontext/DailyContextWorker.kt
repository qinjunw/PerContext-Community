package com.percontext.app.service.dailycontext

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.percontext.app.PerContextApplication
import kotlinx.coroutines.CancellationException

class DailyContextWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val dayKey = inputData.getString(DAY_KEY) ?: return Result.failure()
        val zoneId = inputData.getString(ZONE_ID_KEY) ?: return Result.failure()
        val application = applicationContext as? PerContextApplication ?: return Result.failure()
        val succeeded = executeDailyContextWorker(
            operation = { application.container.generateDailyContext(dayKey, zoneId) },
            onFailure = { error ->
                Log.e(
                    TAG,
                    "Generation failed (${error.javaClass.simpleName}): ${error.message.orEmpty()}",
                )
            },
        )
        return if (succeeded) Result.success() else Result.failure()
    }

    companion object {
        private const val TAG = "DailyContextWorker"
        const val DAY_KEY = "day_key"
        const val ZONE_ID_KEY = "zone_id"
    }
}

internal suspend fun executeDailyContextWorker(
    onFailure: (Throwable) -> Unit = {},
    operation: suspend () -> Unit,
): Boolean = try {
    operation()
    true
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    onFailure(error)
    false
}
