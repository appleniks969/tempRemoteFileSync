package com.sync.filesyncmanager.api

import com.sync.filesyncmanager.api.cache.CacheManager
import com.sync.filesyncmanager.api.network.NetworkManager
import com.sync.filesyncmanager.api.sync.AutoSyncScheduler
import com.sync.filesyncmanager.api.sync.SynchronizationService
import com.sync.filesyncmanager.domain.BatchSyncResult
import com.sync.filesyncmanager.domain.ConfigRepository
import com.sync.filesyncmanager.domain.FileMetadata
import com.sync.filesyncmanager.domain.FileMetadataRepository
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.SyncProgress
import com.sync.filesyncmanager.domain.SyncResult
import com.sync.filesyncmanager.domain.SyncStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import okio.FileSystem

/**
 * Implementation of FileSyncManager using the decomposed services
 */
class FileSyncManagerImpl(
    private val metadataRepo: FileMetadataRepository,
    private val configRepo: ConfigRepository,
    private val networkManager: NetworkManager,
    private val cacheManager: CacheManager,
    private val syncService: SynchronizationService,
    private val autoSyncScheduler: AutoSyncScheduler,
) : FileSyncManager {
    override fun syncFile(fileId: String): Flow<SyncProgress> = syncService.syncFile(fileId)

    override fun syncAll(): Flow<BatchSyncResult> = syncService.syncAll()

    override fun downloadFile(
        fileId: String,
        destinationPath: String?,
    ): Flow<SyncProgress> = syncService.downloadFile(fileId, destinationPath)

    override fun uploadFile(
        fileId: String,
        localPath: String?,
    ): Flow<SyncProgress> = syncService.uploadFile(fileId, localPath)

    override suspend fun getFileMetadata(fileId: String): FileMetadata? = metadataRepo.getFileMetadata(fileId)

    override fun observeFileMetadata(fileId: String): Flow<FileMetadata?> = metadataRepo.observeFileMetadata(fileId)

    override fun observeAllFiles(): Flow<List<FileMetadata>> = metadataRepo.observeAllFileMetadata()

    override suspend fun getAllFiles(): List<FileMetadata> = metadataRepo.getAllFileMetadata()

    override suspend fun addFile(
        metadata: FileMetadata,
        autoSync: Boolean,
    ): SyncResult =
        try {
            metadataRepo.saveFileMetadata(metadata)

            if (autoSync) {
                // We need to collect the flow to execute the operation
                val flowCollector = kotlinx.coroutines.flow.FlowCollector<SyncProgress> { }
                syncFile(metadata.fileId)
                    .catch { error ->
                        // Using FlowCollector to emit progress update
                        flowCollector.emit(
                            SyncProgress(
                                fileId = metadata.fileId,
                                fileName = metadata.fileName,
                                bytesTransferred = 0,
                                totalBytes = metadata.fileSize,
                                progress = 0f,
                                status = com.sync.filesyncmanager.domain.SyncStatus.FAILED,
                                isDownload = false,
                                filePath = metadata.filePath,
                            )
                        )
                    }.collect(flowCollector)
            }

            SyncResult.Success(metadata)
        } catch (e: Exception) {
            SyncResult.Error(metadata.fileId, "Failed to add file: ${e.message}", e.toString())
        }

    override suspend fun removeFile(
        fileId: String,
        deleteLocal: Boolean,
        deleteRemote: Boolean,
    ): SyncResult {
        val metadata =
            metadataRepo.getFileMetadata(fileId)
                ?: return SyncResult.Error(fileId, "File not found")

        return try {
            if (deleteLocal || deleteRemote) {
                if (deleteLocal && metadata.filePath.isNotEmpty()) {
                    // Delete operations are handled in a dedicated method to keep code clean
                    deleteLocalFile(metadata)
                }

                if (deleteRemote && metadata.isUploaded) {
                    // Remote deletion is delegated to the repository
                    // We need to collect the flow to execute the operation
                    val flowCollector = kotlinx.coroutines.flow.FlowCollector<SyncProgress> { }
                    syncService
                        .uploadFile(fileId, null)
                        .collect(flowCollector) // This is needed to ensure the operation completes
                }

                // Update metadata based on deletion options
                if (deleteLocal && deleteRemote) {
                    metadataRepo.deleteFileMetadata(fileId)
                } else {
                    metadataRepo.markFileAsDeleted(fileId)
                }
            }

            SyncResult.Success(metadata)
        } catch (e: Exception) {
            SyncResult.Error(fileId, "Failed to remove file: ${e.message}", e.toString())
        }
    }

    private suspend fun deleteLocalFile(metadata: FileMetadata) {
        // Call to CacheManager is not appropriate here since we're not clearing a cache
        // but explicitly deleting a specific file by user request
        try {
            // This code deliberately does not use CacheManager for this specific use case
            // because CacheManager handles policy-based cache clearing, not
            // explicit user-requested file deletion
            // Create a local repository with a file service using a utility
            val fileService = com.sync.filesyncmanager.util.FileService(
                com.sync.filesyncmanager.FileSyncManagerFactory().getFileSystem()
            )
            val localRepo = com.sync.filesyncmanager.domain.LocalFileRepository(
                fileService = fileService
            )
            // Delete the original file
            localRepo.deleteFile(metadata.filePath)

            // Also delete the extracted directory if it exists
            if (metadata.isExtracted && metadata.extractedPath != null) {
                localRepo.clearCache(metadata.extractedPath)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to delete local file: ${e.message}")
        }
    }

    override suspend fun getConfig(): SyncConfig = configRepo.getSyncConfig()

    override suspend fun updateConfig(config: SyncConfig) {
        configRepo.updateSyncConfig(config)

        // Update auto-sync if needed
        if (config.autoSyncInterval != null) {
            stopAutoSync()
            startAutoSync()
        } else {
            stopAutoSync()
        }
    }

    override fun observeConfig(): Flow<SyncConfig> = configRepo.observeSyncConfig()

    override fun startAutoSync() {
        autoSyncScheduler.startAutoSync()
    }

    override fun stopAutoSync() {
        autoSyncScheduler.stopAutoSync()
    }

    override suspend fun clearCache(): Long {
        val config = configRepo.getSyncConfig()
        return cacheManager.clearCache(config)
    }

    override suspend fun isNetworkAvailable(): Boolean {
        val config = configRepo.getSyncConfig()
        return networkManager.isNetworkSuitable(config)
    }

    override suspend fun resolveConflict(
        fileId: String,
        resolution: SyncStrategy,
    ): SyncResult = syncService.resolveConflict(fileId, resolution)
}
