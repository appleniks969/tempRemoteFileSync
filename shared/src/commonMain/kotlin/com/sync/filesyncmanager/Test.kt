package com.sync.filesyncmanager

import com.sync.filesyncmanager.domain.CacheStrategy
import com.sync.filesyncmanager.domain.NetworkType
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.SyncStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Example class demonstrating the FileSyncManager initialization with configuration
 */
object FileSyncExample {
    /**
     * Creates a sample configuration for the file sync manager
     * @return A sample SyncConfig object
     */
    fun createSampleConfig(): SyncConfig {
        return SyncConfig(
            baseUrl = "https://example.com/api/files",
            syncStrategy = SyncStrategy.BIDIRECTIONAL, 
            cacheStrategy = CacheStrategy.CACHE_RECENT,
            maxConcurrentTransfers = 2,
            autoSyncInterval = 15 * 60 * 1000L, // 15 minutes
            networkType = NetworkType.WIFI_ONLY,
            syncOnlyOnWifi = true,
            authToken = "sample-auth-token",
            compressionEnabled = true,
            unzipFiles = true,
            deleteZipAfterExtract = true,
            retryCount = 3,
            retryDelay = 5000L,
            maxCacheSize = 1024 * 1024 * 100L, // 100 MB
            fileExpiryDuration = 7 * 24 * 60 * 60 * 1000L // 7 days
        )
    }
    
    /**
     * Creates a FileSyncManager instance with default configuration
     * @return Flow of successful initialization
     */
    suspend fun initializeFileSyncManager(): Flow<Boolean> {
        // Use factory to create an instance
        val factory = FileSyncManagerFactory()
        val syncManager = factory.create(createSampleConfig())
        
        // Return a flow indicating successful initialization
        return flowOf(true)
    }
}