package com.sync.filesyncmanager

import com.sync.filesyncmanager.api.FileSyncManager
import com.sync.filesyncmanager.domain.SyncConfig
import io.ktor.client.HttpClient
import okio.FileSystem

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
}
