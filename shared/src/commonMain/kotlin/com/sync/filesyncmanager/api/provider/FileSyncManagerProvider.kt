package com.sync.filesyncmanager.api.provider

import com.sync.filesyncmanager.api.FileSyncManager
import com.sync.filesyncmanager.api.FileSyncManagerImpl
import com.sync.filesyncmanager.api.cache.CacheManager
import com.sync.filesyncmanager.api.cache.CacheManagerImpl
import com.sync.filesyncmanager.api.network.NetworkManager
import com.sync.filesyncmanager.api.network.NetworkManagerImpl
import com.sync.filesyncmanager.api.sync.AutoSyncScheduler
import com.sync.filesyncmanager.api.sync.AutoSyncSchedulerImpl
import com.sync.filesyncmanager.api.sync.SynchronizationService
import com.sync.filesyncmanager.api.sync.SynchronizationServiceImpl
import com.sync.filesyncmanager.domain.ConfigRepository
import com.sync.filesyncmanager.domain.FileMetadataRepository
import com.sync.filesyncmanager.domain.LocalFileRepository
import com.sync.filesyncmanager.domain.RemoteFileRepository
import com.sync.filesyncmanager.util.NetworkMonitor
import com.sync.filesyncmanager.util.SyncScheduler
import com.sync.filesyncmanager.util.ZipService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Provider for FileSyncManager with all its dependencies
 */
class FileSyncManagerProvider(
    private val metadataRepoProvider: Provider<FileMetadataRepository>,
    private val remoteRepoProvider: Provider<RemoteFileRepository>,
    private val localRepoProvider: Provider<LocalFileRepository>,
    private val configRepoProvider: Provider<ConfigRepository>,
    private val networkMonitorProvider: Provider<NetworkMonitor>,
    private val syncSchedulerProvider: Provider<SyncScheduler>,
    private val zipServiceProvider: Provider<ZipService>,
    private val dispatcherProvider: Provider<CoroutineDispatcher>,
) : Provider<FileSyncManager> {
    override fun get(): FileSyncManager {
        val dispatcher = dispatcherProvider.get()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val networkManager = createNetworkManager()
        val cacheManager = createCacheManager()
        val syncService = createSynchronizationService()
        val autoSyncScheduler = createAutoSyncScheduler(syncService, scope)

        return FileSyncManagerImpl(
            metadataRepo = metadataRepoProvider.get(),
            configRepo = configRepoProvider.get(),
            networkManager = networkManager,
            cacheManager = cacheManager,
            syncService = syncService,
            autoSyncScheduler = autoSyncScheduler,
        )
    }

    private fun createNetworkManager(): NetworkManager =
        NetworkManagerImpl(
            networkMonitor = networkMonitorProvider.get(),
        )

    private fun createCacheManager(): CacheManager =
        CacheManagerImpl(
            metadataRepo = metadataRepoProvider.get(),
            localRepo = localRepoProvider.get(),
        )

    private fun createSynchronizationService(): SynchronizationService =
        SynchronizationServiceImpl(
            metadataRepo = metadataRepoProvider.get(),
            remoteRepo = remoteRepoProvider.get(),
            localRepo = localRepoProvider.get(),
            configRepo = configRepoProvider.get(),
            networkManager = createNetworkManager(),
            zipService = zipServiceProvider.get(),
            dispatcher = dispatcherProvider.get(),
        )

    private fun createAutoSyncScheduler(
        syncService: SynchronizationService,
        scope: CoroutineScope,
    ): AutoSyncScheduler =
        AutoSyncSchedulerImpl(
            synchronizationService = syncService,
            configRepo = configRepoProvider.get(),
            networkManager = createNetworkManager(),
            syncScheduler = syncSchedulerProvider.get(),
            scope = scope,
        )
}
