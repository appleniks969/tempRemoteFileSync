package com.sync.filesyncmanager.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970

/**
 * Platform-specific scheduler for periodic tasks on iOS
 */
actual class SyncScheduler actual constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodicJob: kotlinx.coroutines.Job? = null
    private var oneTimeJob: kotlinx.coroutines.Job? = null
    
    /**
     * Task identifier for background operations
     */
    actual val SYNC_TASK_IDENTIFIER: String
        get() = "com.sync.filesyncmanager.backgroundsync"
    
    private val PROCESSING_TASK_IDENTIFIER = "com.sync.filesyncmanager.processing"
    
    /**
     * Schedules a periodic task
     * @param intervalMs The interval in milliseconds
     * @param action The action to perform
     */
    actual suspend fun schedulePeriodic(
        intervalMs: Long,
        action: suspend () -> Unit
    ) {
        // Cancel any existing job
        periodicJob?.cancel()
        
        // Start a new coroutine for periodic execution
        periodicJob = scope.launch {
            while (true) {
                try {
                    action()
                } catch (e: Exception) {
                    println("Error in periodic task: ${e.message}")
                }
                delay(intervalMs)
            }
        }
        
        // Also schedule background task for when app is not active
        submitBackgroundTask()
    }

    /**
     * Schedules a one-time task with delay
     * @param delayMs The delay in milliseconds
     * @param action The action to perform
     */
    actual suspend fun scheduleOnce(delayMs: Long, action: suspend () -> Unit) {
        // Cancel any existing one-time job
        oneTimeJob?.cancel()
        
        // Schedule a new one-time job
        oneTimeJob = scope.launch {
            delay(delayMs)
            try {
                action()
            } catch (e: Exception) {
                println("Error in one-time task: ${e.message}")
            }
        }
    }

    /**
     * Cancels all scheduled tasks
     */
    actual fun cancel() {
        periodicJob?.cancel()
        periodicJob = null
        
        oneTimeJob?.cancel()
        oneTimeJob = null
        
        // Cancel any pending background tasks
        BGTaskScheduler.sharedScheduler.cancelAllTaskRequests()
    }

    /**
     * Submits a background task request
     * Available on iOS but no-op on Android
     */
    actual fun submitBackgroundTask() {
        // Submit both refresh and processing task requests
        submitRefreshTask()
        submitProcessingTask()
    }
    
    /**
     * Submits an app refresh task request
     */
    private fun submitRefreshTask() {
        val request = BGAppRefreshTaskRequest(SYNC_TASK_IDENTIFIER)
        request.earliestBeginDate = NSDate().addingTimeInterval(900.0) // 15 minutes from now
        
        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        } catch (e: Exception) {
            println("Failed to schedule app refresh task: ${e.message}")
        }
    }
    
    /**
     * Submits a processing task request
     */
    private fun submitProcessingTask() {
        val request = BGProcessingTaskRequest(PROCESSING_TASK_IDENTIFIER)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        
        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        } catch (e: Exception) {
            println("Failed to schedule processing task: ${e.message}")
        }
    }

    /**
     * Registers task handlers with the BGTaskScheduler
     * Call this during app initialization
     */
    actual fun registerTasks() {
        val scheduler = BGTaskScheduler.sharedScheduler
        
        // Register app refresh task
        scheduler.registerForTaskWithIdentifier(
            SYNC_TASK_IDENTIFIER,
            null, // Use the main queue
            { task ->
                handleRefreshTask(task)
            }
        )
        
        // Register processing task
        scheduler.registerForTaskWithIdentifier(
            PROCESSING_TASK_IDENTIFIER,
            null, // Use the main queue
            { task ->
                handleProcessingTask(task)
            }
        )
    }
    
    /**
     * Handles the background refresh task
     */
    private fun handleRefreshTask(task: platform.BackgroundTasks.BGTask) {
        // Here we would trigger a quick sync
        // This is just a placeholder - you'd need to call your sync method here
        
        // When done, call setTaskCompletedWithSuccess
        task.setTaskCompletedWithSuccess(true)
        
        // Schedule the next task
        submitRefreshTask()
    }
    
    /**
     * Handles the background processing task
     */
    private fun handleProcessingTask(task: platform.BackgroundTasks.BGTask) {
        // Here we would trigger a more intensive sync operation
        // This is just a placeholder - you'd need to call your sync method here
        
        // When done, call setTaskCompletedWithSuccess
        task.setTaskCompletedWithSuccess(true)
        
        // Schedule the next task
        submitProcessingTask()
    }
}