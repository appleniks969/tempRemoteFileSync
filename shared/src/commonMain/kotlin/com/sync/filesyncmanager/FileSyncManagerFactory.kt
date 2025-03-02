package com.sync.filesyncmanager

import com.sync.filesyncmanager.api.FileSyncManager
import com.sync.filesyncmanager.data.local.FileSyncDatabase
import com.sync.filesyncmanager.domain.SyncConfig
import io.ktor.client.HttpClient
import okio.FileSystem

/**
 * Factory for creating FileSyncManager instances
 */
expect class FileSyncManagerFactory() {
    /**
     * Creates a FileSyncManager instance
     */
    suspend fun create(
        initialConfig: SyncConfig? = null
    ): FileSyncManager

    /**
     * Creates an HTTP client
     */
    fun createHttpClient(): HttpClient

    /**
     * Gets the filesystem for this platform
     */
    fun getFileSystem(): FileSystem

    /**
     * Gets a database instance
     */
    fun getDatabase(): FileSyncDatabase
}