package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.HealthConnectRepositoryImpl
import com.example.data.repository.HealthPermissionMissingException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Periodic background sync. A missing permission is not an error worth retrying (the user has to
 * grant it in the app), so it ends the run quietly. Other failures retry with WorkManager backoff.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!SyncPrefs.isAutoSyncEnabled(applicationContext)) return Result.success()
        return try {
            val outcome = HealthConnectRepositoryImpl(applicationContext).syncAllRunningData()
            val error = outcome.exceptionOrNull()
            when {
                error == null -> {
                    SyncPrefs.markSynced(applicationContext)
                    Result.success()
                }
                error is HealthPermissionMissingException -> Result.success()
                runAttemptCount < 3 -> Result.retry()
                else -> Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SyncWorker", "Background sync failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
