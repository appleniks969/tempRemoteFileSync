package com.sync.filesyncmanager.api

import com.sync.filesyncmanager.domain.BatchSyncResult
import com.sync.filesyncmanager.domain.FileMetadata
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.SyncProgress
import com.sync.filesyncmanager.domain.SyncResult
import com.sync.filesyncmanager.domain.SyncStrategy
import kotlinx.coroutines.flow.Flow

/**
 * Interface for file synchronization operations
 */
interface FileSyncManager {
    /**
     * Synchronizes a specific file
     * @param fileId ID of the file to sync
     * @return Flow of sync progress updates
     */
    fun syncFile(fileId: String): Flow<SyncProgress>

    /**
     * Synchronizes all files
     * @return Flow of batch sync results
     */
    fun syncAll(): Flow<BatchSyncResult>

    /**
     * Downloads a file
     * @param fileId ID of the file to download
     * @param destinationPath Optional path where to save the file
     * @return Flow of sync progress updates
     */
    fun downloadFile(
        fileId: String,
        destinationPath: String?,
    ): Flow<SyncProgress>

    /**
     * Uploads a file
     * @param fileId ID of the file to upload
     * @param localPath Optional path to the local file
     * @return Flow of sync progress updates
     */
    fun uploadFile(
        fileId: String,
        localPath: String?,
    ): Flow<SyncProgress>

    /**
     * Gets metadata for a specific file
     * @param fileId ID of the file
     * @return File metadata or null if not found
     */
    suspend fun getFileMetadata(fileId: String): FileMetadata?

    /**
     * Observes changes to file metadata
     * @param fileId ID of the file to observe
     * @return Flow of file metadata updates
     */
    fun observeFileMetadata(fileId: String): Flow<FileMetadata?>

    /**
     * Observes all files
     * @return Flow of all file metadata
     */
    fun observeAllFiles(): Flow<List<FileMetadata>>

    /**
     * Gets all files
     * @return List of all file metadata
     */
    suspend fun getAllFiles(): List<FileMetadata>

    /**
     * Adds a file to be synced
     * @param metadata Metadata of the file to add
     * @param autoSync Whether to sync the file immediately
     * @return Result of the operation
     */
    suspend fun addFile(
        metadata: FileMetadata,
        autoSync: Boolean,
    ): SyncResult

    /**
     * Removes a file
     * @param fileId ID of the file to remove
     * @param deleteLocal Whether to delete the local file
     * @param deleteRemote Whether to delete the remote file
     * @return Result of the operation
     */
    suspend fun removeFile(
        fileId: String,
        deleteLocal: Boolean,
        deleteRemote: Boolean,
    ): SyncResult

    /**
     * Gets the current sync configuration
     * @return The current sync configuration
     */
    suspend fun getConfig(): SyncConfig

    /**
     * Updates the sync configuration
     * @param config The new configuration
     */
    suspend fun updateConfig(config: SyncConfig)

    /**
     * Observes changes to the sync configuration
     * @return Flow of configuration updates
     */
    fun observeConfig(): Flow<SyncConfig>

    /**
     * Starts automatic synchronization
     */
    fun startAutoSync()

    /**
     * Stops automatic synchronization
     */
    fun stopAutoSync()

    /**
     * Clears the local cache
     * @return The number of bytes cleared
     */
    suspend fun clearCache(): Long

    /**
     * Checks if the network is available for sync
     * @return Whether the network is available
     */
    suspend fun isNetworkAvailable(): Boolean

    /**
     * Resolves a file conflict
     * @param fileId ID of the file in conflict
     * @param resolution The resolution strategy
     * @return Result of the operation
     */
    suspend fun resolveConflict(
        fileId: String,
        resolution: SyncStrategy,
    ): SyncResult
}
