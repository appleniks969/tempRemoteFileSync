package com.sync.filesyncmanager.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sync.filesyncmanager.AppContextProvider
import com.sync.filesyncmanager.FileSyncManagerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Android implementation of SyncScheduler using WorkManager
 */
actual class SyncScheduler actual constructor() {
    actual val syncTaskIdentifier: String = "com.sync.filesyncmanager.SYNC_TASK"

    private val scope = CoroutineScope(IODispatcher + SupervisorJob())
    private var periodicJob: Job? = null

    actual suspend fun schedulePeriodic(intervalMs: Long, action: suspend () -> Unit) {
        // Cancel any existing job
        periodicJob?.cancel()

        // Start a new periodic job
        periodicJob = scope.launch {
            while (isActive) {
                try {
                    action()
                } catch (e: Exception) {
                    println("Error in periodic sync: ${e.message}")
                }
                delay(intervalMs)
            }
        }

        // Also schedule with WorkManager for reliability
        try {
            val context = AppContextProvider.context

            // Need minimum interval of 15 minutes for WorkManager
            val workIntervalMs = maxOf(intervalMs, 15 * 60 * 1000)

            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                workIntervalMs, TimeUnit.MILLISECONDS
            ).build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    syncTaskIdentifier,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
        } catch (e: Exception) {
            println("Could not schedule WorkManager task: ${e.message}")
        }
    }

    actual suspend fun scheduleOnce(delayMs: Long, action: suspend () -> Unit) {
        scope.launch {
            delay(delayMs)
            try {
                action()
            } catch (e: Exception) {
                println("Error in one-time sync: ${e.message}")
            }
        }

        // Also schedule with WorkManager for reliability
        try {
            val context = AppContextProvider.context

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        } catch (e: Exception) {
            println("Could not schedule WorkManager task: ${e.message}")
        }
    }

    actual fun cancel() {
        periodicJob?.cancel()
        periodicJob = null

        try {
            val context = AppContextProvider.context
            WorkManager.getInstance(context).cancelUniqueWork(syncTaskIdentifier)
        } catch (e: Exception) {
            // Ignore if context is not available
        }
    }

    // No-op for Android
    actual fun submitBackgroundTask() {
        // Not needed for Android, handled by WorkManager
    }

    // No-op for Android
    actual fun registerTasks() {
        // Not needed for Android, handled by WorkManager
    }

    /**
     * WorkManager worker for background file synchronization
     */
    class SyncWorker(
        appContext: Context,
        workerParams: WorkerParameters
    ) : CoroutineWorker(appContext, workerParams) {

        companion object {
            private const val TAG = "SyncWorker"
        }

        override suspend fun doWork(): Result {
            Log.d(TAG, "Starting background sync")

            return try {
                // Create the sync manager
                val factory = FileSyncManagerFactory()
                val syncManager = factory.create()

                // Perform sync
                val result = syncManager.syncAll()
                    .catch { error ->
                        Log.e(TAG, "Error during sync", error)
                        // Return failure if we get an error
                        Result.failure()
                    }
                    .firstOrNull()

                if (result != null) {
                    Log.d(
                        TAG,
                        "Sync completed: Success=${result.successCount}, Failed=${result.failedCount}, Conflicts=${result.conflictCount}"
                    )

                    // If there are failures, consider scheduling a retry
                    if (result.failedCount > 0) {
                        if (runAttemptCount < 3) {
                            Result.retry()
                        } else {
                            Result.failure()
                        }
                    } else {
                        Result.success()
                    }
                } else {
                    Log.d(TAG, "Sync completed without result")
                    Result.success()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during sync", e)
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}
