package com.sync.filesyncmanager.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sync.filesyncmanager.AppContextProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Android implementation of SyncScheduler using WorkManager
 */
actual class SyncScheduler actual constructor() {
    actual val SYNC_TASK_IDENTIFIER: String = "com.sync.filesyncmanager.SYNC_TASK"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
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
                    SYNC_TASK_IDENTIFIER,
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
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_TASK_IDENTIFIER)
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
     * Worker class for WorkManager integration
     */
    class SyncWorker(appContext: Context, params: WorkerParameters) :
        CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            try {
                // This would need to connect to the actual sync logic in real implementation
                return Result.success()
            } catch (e: Exception) {
                return Result.retry()
            }
        }
    }
}
