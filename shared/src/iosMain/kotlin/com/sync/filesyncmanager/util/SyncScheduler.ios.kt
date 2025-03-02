package com.sync.filesyncmanager.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.NSTimer
import platform.Foundation.addTimeInterval
import platform.Foundation.alloc
import platform.Foundation.init
import platform.Foundation.NSOperationQueue
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import kotlin.native.concurrent.freeze

/**
 * iOS implementation of SyncScheduler using NSTimer and BGTaskScheduler
 */
actual class SyncScheduler actual constructor() {
    actual val SYNC_TASK_IDENTIFIER: String = "com.sync.filesyncmanager.sync-task"
    private val BACKGROUND_PROCESSING_TASK = "com.sync.filesyncmanager.background-processing"
    private val BACKGROUND_REFRESH_TASK = "com.sync.filesyncmanager.background-refresh"
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var periodicJob: Job? = null
    private var timers = mutableListOf<NSTimer>()
    
    // The background task closure needs to be frozen to be used across threads
    private var backgroundTaskAction: (suspend () -> Unit)? = null
    
    /**
     * Schedules a periodic sync action on iOS
     */
    actual suspend fun schedulePeriodic(intervalMs: Long, action: suspend () -> Unit) {
        // Cancel any existing job
        periodicJob?.cancel()
        
        // Store the action for background tasks
        backgroundTaskAction = action.freeze()
        
        // Start a new periodic job
        periodicJob = scope.launch {
            while (isActive) {
                try {
                    action()
                } catch (e: Exception) {
                    println("Error in periodic sync: ${e}")
                }
                delay(intervalMs)
            }
        }
        
        // Also create an NSTimer for when the app is in foreground
        val intervalSec = intervalMs / 1000.0
        val timer = NSTimer.scheduledTimerWithTimeInterval(
            intervalSec,
            true,
            {
                scope.launch {
                    try {
                        action()
                    } catch (e: Exception) {
                        println("Error in timer sync: ${e}")
                    }
                }
            }
        )
        
        timers.add(timer)
        NSRunLoop.mainRunLoop.addTimer(timer, NSDefaultRunLoopMode)
        
        // Schedule background task
        scheduleBackgroundTask()
    }
    
    /**
     * Schedules a one-time sync action on iOS
     */
    actual suspend fun scheduleOnce(delayMs: Long, action: suspend () -> Unit) {
        // Store the action for background tasks if needed
        backgroundTaskAction = action.freeze()
        
        // Launch coroutine for when app is active
        scope.launch {
            delay(delayMs)
            try {
                action()
            } catch (e: Exception) {
                println("Error in one-time sync: ${e}")
            }
        }
        
        // Also create a one-time NSTimer
        val delaySec = delayMs / 1000.0
        val timer = NSTimer.scheduledTimerWithTimeInterval(
            delaySec,
            false,
            {
                scope.launch {
                    try {
                        action()
                    } catch (e: Exception) {
                        println("Error in timer sync: ${e}")
                    }
                }
            }
        )
        
        timers.add(timer)
        NSRunLoop.mainRunLoop.addTimer(timer, NSDefaultRunLoopMode)
        
        // Also schedule a background task just in case
        scheduleBackgroundTask()
    }
    
    /**
     * Cancels all scheduled sync operations on iOS
     */
    actual fun cancel() {
        periodicJob?.cancel()
        periodicJob = null
        
        // Invalidate and remove all timers
        timers.forEach { it.invalidate() }
        timers.clear()
        
        // Cancel background tasks
        BGTaskScheduler.shared.cancelAllTaskRequests()
    }
    
    /**
     * Schedules a background task using BGTaskScheduler
     */
    private fun scheduleBackgroundTask() {
        try {
            // Schedule app refresh task
            val refreshRequest = BGAppRefreshTaskRequest(BACKGROUND_REFRESH_TASK)
            refreshRequest.earliestBeginDate = NSDate().addTimeInterval(15 * 60.0) // 15 minutes from now
            BGTaskScheduler.shared.submitTaskRequest(refreshRequest, null)
            
            // Schedule processing task
            val processingRequest = BGProcessingTaskRequest(BACKGROUND_PROCESSING_TASK)
            processingRequest.requiresNetworkConnectivity = true
            processingRequest.requiresExternalPower = false
            processingRequest.earliestBeginDate = NSDate().addTimeInterval(60 * 60.0) // 1 hour from now
            BGTaskScheduler.shared.submitTaskRequest(processingRequest, null)
        } catch (e: Exception) {
            println("Failed to schedule background task: ${e}")
        }
    }
    
    /**
     * Submits a background task to run
     * This is used when iOS needs to finish a long-running operation
     */
    actual fun submitBackgroundTask() {
        val action = backgroundTaskAction ?: return
        
        NSOperationQueue.mainQueue.addOperationWithBlock {
            scope.launch {
                try {
                    action()
                } catch (e: Exception) {
                    println("Error in background task: ${e}")
                }
            }
        }
    }
    
    /**
     * Registers the background tasks with the system
     * This should be called during app initialization
     */
    actual fun registerTasks() {
        BGTaskScheduler.shared.registerForTaskWithIdentifier(
            BACKGROUND_REFRESH_TASK,
            null
        ) { task ->
            // Execute the background task
            submitBackgroundTask()
            
            // Mark the task as complete
            task.setTaskCompletedWithSuccess(true)
            
            // Reschedule
            scheduleBackgroundTask()
        }
        
        BGTaskScheduler.shared.registerForTaskWithIdentifier(
            BACKGROUND_PROCESSING_TASK,
            null
        ) { task ->
            // Execute the background task
            submitBackgroundTask()
            
            // Mark the task as complete
            task.setTaskCompletedWithSuccess(true)
            
            // Reschedule
            scheduleBackgroundTask()
        }
    }
}