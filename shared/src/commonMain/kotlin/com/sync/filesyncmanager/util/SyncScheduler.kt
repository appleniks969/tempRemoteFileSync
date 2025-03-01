package com.sync.filesyncmanager.util

/**
 * Platform-specific scheduler for periodic tasks
 */
expect class SyncScheduler() {
    /**
     * Task identifier for background operations
     */
    val SYNC_TASK_IDENTIFIER: String

    /**
     * Schedules a periodic task
     * @param intervalMs The interval in milliseconds
     * @param action The action to perform
     */
    suspend fun schedulePeriodic(intervalMs: Long, action: suspend () -> Unit)

    /**
     * Schedules a one-time task with delay
     * @param delayMs The delay in milliseconds
     * @param action The action to perform
     */
    suspend fun scheduleOnce(delayMs: Long, action: suspend () -> Unit)

    /**
     * Cancels all scheduled tasks
     */
    fun cancel()

    /**
     * Submits a background task request
     * Available on iOS but no-op on Android
     */
    fun submitBackgroundTask()

    /**
     * Registers task handlers
     * Available on iOS but no-op on Android
     */
    fun registerTasks()
}
