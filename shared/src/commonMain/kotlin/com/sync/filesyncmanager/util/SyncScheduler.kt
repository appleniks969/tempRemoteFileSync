package com.sync.filesyncmanager.util

/**
 * Cross-platform scheduler for periodic and one-time sync operations
 */
expect class SyncScheduler() {
    /**
     * Unique identifier for sync tasks
     */
    val SYNC_TASK_IDENTIFIER: String

    /**
     * Schedules a periodic sync action
     * 
     * @param intervalMs interval in milliseconds
     * @param action action to execute
     */
    suspend fun schedulePeriodic(intervalMs: Long, action: suspend () -> Unit)

    /**
     * Schedules a one-time sync action
     * 
     * @param delayMs delay in milliseconds
     * @param action action to execute
     */
    suspend fun scheduleOnce(delayMs: Long, action: suspend () -> Unit)

    /**
     * Cancels all scheduled sync operations
     */
    fun cancel()

    /**
     * Submits a background task
     * This is needed for some platforms to finish long-running operations
     */
    fun submitBackgroundTask()

    /**
     * Registers the background sync tasks with the platform
     * This is needed for some platforms to register the tasks initially
     */
    fun registerTasks()
}