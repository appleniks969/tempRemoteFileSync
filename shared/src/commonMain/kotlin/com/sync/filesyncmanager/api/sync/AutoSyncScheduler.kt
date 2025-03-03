package com.sync.filesyncmanager.api.sync

import com.sync.filesyncmanager.api.network.NetworkManager
import com.sync.filesyncmanager.domain.ConfigRepository
import com.sync.filesyncmanager.util.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Manages scheduling of automatic synchronization
 */
interface AutoSyncScheduler {
    /**
     * Starts automatic synchronization based on the current configuration
     */
    fun startAutoSync()

    /**
     * Stops automatic synchronization
     */
    fun stopAutoSync()
}

/**
 * Implementation of AutoSyncScheduler
 */
class AutoSyncSchedulerImpl(
    private val synchronizationService: SynchronizationService,
    private val configRepo: ConfigRepository,
    private val networkManager: NetworkManager,
    private val syncScheduler: SyncScheduler,
    private val scope: CoroutineScope,
) : AutoSyncScheduler {
    private var autoSyncJob: Job? = null

    override fun startAutoSync() {
        autoSyncJob?.cancel()

        autoSyncJob =
            scope.launch {
                val config = configRepo.getSyncConfig()
                val interval = config.autoSyncInterval ?: return@launch

                syncScheduler.schedulePeriodic(interval) {
                    if (networkManager.isNetworkSuitable(config)) {
                        val flowCollector = kotlinx.coroutines.flow.FlowCollector<com.sync.filesyncmanager.domain.BatchSyncResult> { }
                        synchronizationService.syncAll().collect(flowCollector)
                    }
                }

                // Schedule a background task on iOS
                syncScheduler.submitBackgroundTask()
            }
    }

    override fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
        syncScheduler.cancel()
    }
}
