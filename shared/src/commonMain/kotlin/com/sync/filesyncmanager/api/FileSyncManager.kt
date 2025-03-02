package com.sync.filesyncmanager.api

import com.sync.filesyncmanager.domain.BatchSyncResult
import com.sync.filesyncmanager.domain.CacheStrategy
import com.sync.filesyncmanager.domain.ConfigRepository
import com.sync.filesyncmanager.domain.FileMetadata
import com.sync.filesyncmanager.domain.FileMetadataRepository
import com.sync.filesyncmanager.domain.LocalFileRepository
import com.sync.filesyncmanager.domain.NetworkType
import com.sync.filesyncmanager.domain.RemoteFileRepository
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.SyncProgress
import com.sync.filesyncmanager.domain.SyncResult
import com.sync.filesyncmanager.domain.SyncStatus
import com.sync.filesyncmanager.domain.SyncStrategy
import com.sync.filesyncmanager.util.NetworkMonitor
import com.sync.filesyncmanager.util.SyncScheduler
import com.sync.filesyncmanager.util.ZipService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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
    fun downloadFile(fileId: String, destinationPath: String?): Flow<SyncProgress>

    /**
     * Uploads a file
     * @param fileId ID of the file to upload
     * @param localPath Optional path to the local file
     * @return Flow of sync progress updates
     */
    fun uploadFile(fileId: String, localPath: String?): Flow<SyncProgress>

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
    suspend fun addFile(metadata: FileMetadata, autoSync: Boolean): SyncResult

    /**
     * Removes a file
     * @param fileId ID of the file to remove
     * @param deleteLocal Whether to delete the local file
     * @param deleteRemote Whether to delete the remote file
     * @return Result of the operation
     */
    suspend fun removeFile(fileId: String, deleteLocal: Boolean, deleteRemote: Boolean): SyncResult

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
    suspend fun resolveConflict(fileId: String, resolution: SyncStrategy): SyncResult
}

/**
 * Implementation of the FileSyncManager interface
 */
class FileSyncManagerImpl(
    private val metadataRepo: FileMetadataRepository,
    private val remoteRepo: RemoteFileRepository,
    private val localRepo: LocalFileRepository,
    private val configRepo: ConfigRepository,
    private val networkMonitor: NetworkMonitor,
    private val syncScheduler: SyncScheduler,
    private val zipService: ZipService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : FileSyncManager {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var autoSyncJob: Job? = null

    override fun syncFile(fileId: String): Flow<SyncProgress> = flow {
        val metadata = metadataRepo.getFileMetadata(fileId)
            ?: throw IllegalArgumentException("File not found: $fileId")
        val config = configRepo.getSyncConfig()

        if (!isNetworkSuitable(config)) {
            emit(
                SyncProgress(
                    fileId = fileId,
                    fileName = metadata.fileName,
                    bytesTransferred = 0,
                    totalBytes = metadata.fileSize,
                    progress = 0f,
                    status = SyncStatus.FAILED,
                    isDownload = false
                )
            )
            throw IllegalStateException("Network unavailable or unsuitable for sync")
        }

        when (config.syncStrategy) {
            SyncStrategy.DOWNLOAD_ONLY -> {
                if (!metadata.isDownloaded) {
                    downloadFile(fileId, null).collect { emit(it) }
                }
            }

            SyncStrategy.UPLOAD_ONLY -> {
                if (!metadata.isUploaded) {
                    uploadFile(fileId, null).collect { emit(it) }
                }
            }

            SyncStrategy.BIDIRECTIONAL, SyncStrategy.LOCAL_WINS, SyncStrategy.REMOTE_WINS, SyncStrategy.NEWEST_WINS -> {
                val remoteMetadata = remoteRepo.getRemoteMetadata(fileId)

                if (remoteMetadata == null) {
                    // File doesn't exist remotely, upload it
                    if (localRepo.fileExists(metadata.filePath)) {
                        uploadFile(fileId, null).collect { emit(it) }
                    }
                } else if (!metadata.isDownloaded) {
                    // Local file doesn't exist, download it
                    downloadFile(fileId, null).collect { emit(it) }
                } else {
                    // Both exist, check for conflicts
                    val localChecksum = localRepo.getFileChecksum(metadata.filePath) ?: ""
                    val remoteChecksum = remoteRepo.getFileChecksum(fileId) ?: ""

                    if (localChecksum == remoteChecksum) {
                        // Files are the same, update status
                        metadataRepo.updateSyncStatus(fileId, SyncStatus.SYNCED)
                        emit(
                            SyncProgress(
                                fileId = fileId,
                                fileName = metadata.fileName,
                                bytesTransferred = metadata.fileSize,
                                totalBytes = metadata.fileSize,
                                progress = 1f,
                                status = SyncStatus.SYNCED,
                                isDownload = false,
                                filePath = metadata.filePath
                            )
                        )
                    } else {
                        // Conflict resolution
                        when (config.syncStrategy) {
                            SyncStrategy.LOCAL_WINS -> {
                                uploadFile(fileId, null).collect { emit(it) }
                            }

                            SyncStrategy.REMOTE_WINS -> {
                                downloadFile(fileId, null).collect { emit(it) }
                            }

                            SyncStrategy.NEWEST_WINS -> {
                                if (metadata.lastModified > remoteMetadata.lastModified) {
                                    uploadFile(fileId, null).collect { emit(it) }
                                } else {
                                    downloadFile(fileId, null).collect { emit(it) }
                                }
                            }

                            else -> {
                                // Mark as conflict for manual resolution
                                metadataRepo.updateSyncStatus(fileId, SyncStatus.CONFLICT)
                                emit(
                                    SyncProgress(
                                        fileId = fileId,
                                        fileName = metadata.fileName,
                                        bytesTransferred = 0,
                                        totalBytes = metadata.fileSize,
                                        progress = 0f,
                                        status = SyncStatus.CONFLICT,
                                        isDownload = false,
                                        filePath = metadata.filePath
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }.flowOn(dispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun syncAll(): Flow<BatchSyncResult> = flow {
        val config = configRepo.getSyncConfig()
        if (!isNetworkSuitable(config)) {
            emit(BatchSyncResult(0, 0, 0, emptyList(), 0))
            return@flow
        }

        val pendingFiles = metadataRepo.getUnsyncedFiles()
        var successCount = 0
        var failedCount = 0
        var conflictCount = 0
        val failedFiles = mutableListOf<Pair<String, String>>()
        var totalProcessed = 0

        pendingFiles.asFlow()
            .flatMapMerge(concurrency = config.maxConcurrentTransfers) { metadata ->
                syncFile(metadata.fileId)
                    .catch { error ->
                        failedCount++
                        failedFiles.add(metadata.fileId to (error.message ?: "Unknown error"))
                        emit(
                            SyncProgress(
                                fileId = metadata.fileId,
                                fileName = metadata.fileName,
                                bytesTransferred = 0,
                                totalBytes = metadata.fileSize,
                                progress = 0f,
                                status = SyncStatus.FAILED,
                                isDownload = false,
                                filePath = metadata.filePath
                            )
                        )
                    }
                    .onCompletion {
                        totalProcessed++
                        emit(
                            BatchSyncResult(
                                successCount = successCount,
                                failedCount = failedCount,
                                conflictCount = conflictCount,
                                failedFiles = failedFiles,
                                totalProcessed = totalProcessed
                            )
                        )
                    }
                    .map { progress ->
                        if (progress.status == SyncStatus.SYNCED) {
                            successCount++
                        } else if (progress.status == SyncStatus.CONFLICT) {
                            conflictCount++
                        } else if (progress.status == SyncStatus.FAILED) {
                            failedCount++
                        }
                        progress
                    }
            }
            .collect()

        emit(
            BatchSyncResult(
                successCount = successCount,
                failedCount = failedCount,
                conflictCount = conflictCount,
                failedFiles = failedFiles,
                totalProcessed = totalProcessed
            )
        )
    }.flowOn(dispatcher)

    override fun downloadFile(fileId: String, destinationPath: String?): Flow<SyncProgress> = flow {
        val metadata = metadataRepo.getFileMetadata(fileId)
            ?: throw IllegalArgumentException("File not found: $fileId")
        val targetPath = destinationPath ?: metadata.filePath

        // Create parent directory if it doesn't exist
        val parentDir = targetPath.substringBeforeLast('/', "")
        if (parentDir.isNotEmpty() && !localRepo.fileExists(parentDir)) {
            localRepo.createDirectory(parentDir)
        }

        metadataRepo.updateSyncStatus(fileId, SyncStatus.DOWNLOADING)

        remoteRepo.downloadFile(fileId, targetPath)
            .collect { progress ->
                emit(progress)

                if (progress.progress >= 1.0f) {
                    val config = configRepo.getSyncConfig()
                    val checksum = localRepo.getFileChecksum(targetPath)

                    // Check if the file is a ZIP and unzip is enabled
                    val isZip = targetPath.lowercase().endsWith(".zip")
                    var extractedPath: String? = null
                    var isExtracted = false

                    if (isZip && config.unzipFiles) {
                        // Create extract directory path
                        val extractDir = "$targetPath-extracted"
                        isExtracted = zipService.extractZip(targetPath, extractDir, config.deleteZipAfterExtract)
                        extractedPath = if (isExtracted) extractDir else null
                    }

                    // Get the updated metadata object
                    val updatedMetadata = metadata.copy(
                        isZipFile = isZip,
                        extractedPath = extractedPath,
                        isExtracted = isExtracted
                    )

                    // Save the updated metadata
                    metadataRepo.saveFileMetadata(updatedMetadata)

                    // Update download status
                    metadataRepo.updateDownloadStatus(
                        fileId = fileId,
                        isDownloaded = true,
                        checksum = checksum,
                        status = SyncStatus.SYNCED
                    )

                    // Emit final progress with either the extracted path or original path
                    if (isExtracted) {
                        emit(progress.copy(filePath = extractedPath))
                    }
                }
            }
    }.flowOn(dispatcher)

    override fun uploadFile(fileId: String, localPath: String?): Flow<SyncProgress> = flow {
        val metadata = metadataRepo.getFileMetadata(fileId)
            ?: throw IllegalArgumentException("File not found: $fileId")
        val sourcePath = localPath ?: metadata.filePath

        if (!localRepo.fileExists(sourcePath)) {
            throw IllegalArgumentException("Local file does not exist: $sourcePath")
        }

        metadataRepo.updateSyncStatus(fileId, SyncStatus.UPLOADING)

        remoteRepo.uploadFile(fileId, sourcePath)
            .collect { progress ->
                emit(progress)

                if (progress.progress >= 1.0f) {
                    val remoteChecksum = remoteRepo.getFileChecksum(fileId)
                    metadataRepo.updateUploadStatus(
                        fileId = fileId,
                        isUploaded = true,
                        checksum = remoteChecksum,
                        status = SyncStatus.SYNCED
                    )
                }
            }
    }.flowOn(dispatcher)

    override suspend fun getFileMetadata(fileId: String): FileMetadata? {
        return metadataRepo.getFileMetadata(fileId)
    }

    override fun observeFileMetadata(fileId: String): Flow<FileMetadata?> {
        return metadataRepo.observeFileMetadata(fileId)
    }

    override fun observeAllFiles(): Flow<List<FileMetadata>> {
        return metadataRepo.observeAllFileMetadata()
    }

    override suspend fun getAllFiles(): List<FileMetadata> {
        return metadataRepo.getAllFileMetadata()
    }

    override suspend fun addFile(metadata: FileMetadata, autoSync: Boolean): SyncResult {
        return try {
            metadataRepo.saveFileMetadata(metadata)

            if (autoSync) {
                syncFile(metadata.fileId)
                    .catch { error ->
                        emit(
                            SyncProgress(
                                fileId = metadata.fileId,
                                fileName = metadata.fileName,
                                bytesTransferred = 0,
                                totalBytes = metadata.fileSize,
                                progress = 0f,
                                status = SyncStatus.FAILED,
                                isDownload = false,
                                filePath = metadata.filePath
                            )
                        )
                    }
                    .collect()
            }

            SyncResult.Success(metadata)
        } catch (e: Exception) {
            SyncResult.Error(metadata.fileId, "Failed to add file: ${e.message}", e.toString())
        }
    }

    override suspend fun removeFile(
        fileId: String,
        deleteLocal: Boolean,
        deleteRemote: Boolean
    ): SyncResult {
        return try {
            val metadata = metadataRepo.getFileMetadata(fileId) ?: return SyncResult.Error(
                fileId,
                "File not found"
            )

            if (deleteLocal && metadata.isDownloaded) {
                // Delete the original file
                localRepo.deleteFile(metadata.filePath)

                // Also delete the extracted directory if it exists
                if (metadata.isExtracted && metadata.extractedPath != null) {
                    localRepo.clearCache(metadata.extractedPath)
                }
            }

            if (deleteRemote && metadata.isUploaded) {
                remoteRepo.deleteRemoteFile(fileId)
            }

            if (deleteLocal && deleteRemote) {
                metadataRepo.deleteFileMetadata(fileId)
            } else {
                metadataRepo.markFileAsDeleted(fileId)
            }

            SyncResult.Success(metadata)
        } catch (e: Exception) {
            SyncResult.Error(fileId, "Failed to remove file: ${e.message}", e.toString())
        }
    }

    override suspend fun getConfig(): SyncConfig {
        return configRepo.getSyncConfig()
    }

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

    override fun observeConfig(): Flow<SyncConfig> {
        return configRepo.observeSyncConfig()
    }

    override fun startAutoSync() {
        autoSyncJob?.cancel()

        autoSyncJob = scope.launch {
            val config = configRepo.getSyncConfig()
            val interval = config.autoSyncInterval ?: return@launch

            syncScheduler.schedulePeriodic(interval) {
                if (isNetworkSuitable(config)) {
                    syncAll().collect()
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

    override suspend fun clearCache(): Long {
        val config = configRepo.getSyncConfig()
        val currentSize = localRepo.getTotalCacheSize()

        return when (config.cacheStrategy) {
            CacheStrategy.NO_CACHE -> {
                // Delete all local files
                metadataRepo.getAllFileMetadata().forEach { metadata ->
                    if (metadata.isDownloaded) {
                        localRepo.deleteFile(metadata.filePath)

                        // Also delete extracted files
                        if (metadata.isExtracted && metadata.extractedPath != null) {
                            localRepo.clearCache(metadata.extractedPath)
                        }

                        metadataRepo.updateDownloadStatus(
                            fileId = metadata.fileId,
                            isDownloaded = false,
                            checksum = null,
                            status = SyncStatus.PENDING
                        )
                    }
                }
                currentSize
            }

            CacheStrategy.CACHE_RECENT -> {
                // Keep only recently accessed files
                val now = Clock.System.now()
                val filesToDelete = metadataRepo.getAllFileMetadata().filter { metadata ->
                    metadata.isDownloaded && metadata.lastSyncTime != null &&
                            ((now.toEpochMilliseconds() - metadata.lastSyncTime.toEpochMilliseconds()) >
                                    (config.fileExpiryDuration ?: Long.MAX_VALUE))
                }

                var bytesCleared = 0L
                filesToDelete.forEach { metadata ->
                    if (localRepo.deleteFile(metadata.filePath)) {
                        bytesCleared += metadata.fileSize

                        // Also delete extracted files
                        if (metadata.isExtracted && metadata.extractedPath != null) {
                            localRepo.clearCache(metadata.extractedPath)
                        }

                        metadataRepo.updateDownloadStatus(
                            fileId = metadata.fileId,
                            isDownloaded = false,
                            checksum = null,
                            status = SyncStatus.PENDING
                        )
                    }
                }
                bytesCleared
            }

            CacheStrategy.CACHE_PRIORITY -> {
                // Delete low priority files if over size limit
                if (config.maxCacheSize != null && currentSize > config.maxCacheSize) {
                    val exceedingBytes = currentSize - config.maxCacheSize
                    val filesToDelete = metadataRepo.getAllFileMetadata()
                        .filter { it.isDownloaded }
                        .sortedBy { it.priority }

                    var bytesCleared = 0L
                    for (metadata in filesToDelete) {
                        if (bytesCleared >= exceedingBytes) break

                        if (localRepo.deleteFile(metadata.filePath)) {
                            bytesCleared += metadata.fileSize

                            // Also delete extracted files
                            if (metadata.isExtracted && metadata.extractedPath != null) {
                                localRepo.clearCache(metadata.extractedPath)
                            }

                            metadataRepo.updateDownloadStatus(
                                fileId = metadata.fileId,
                                isDownloaded = false,
                                checksum = null,
                                status = SyncStatus.PENDING
                            )
                        }
                    }
                    bytesCleared
                } else {
                    0L
                }
            }

            else -> 0L
        }
    }

    override suspend fun isNetworkAvailable(): Boolean {
        val config = configRepo.getSyncConfig()
        return isNetworkSuitable(config)
    }

    override suspend fun resolveConflict(fileId: String, resolution: SyncStrategy): SyncResult {
        val metadata = metadataRepo.getFileMetadata(fileId) ?: return SyncResult.Error(
            fileId,
            "File not found"
        )

        if (metadata.syncStatus != SyncStatus.CONFLICT) {
            return SyncResult.Error(fileId, "File is not in conflict state")
        }

        return when (resolution) {
            SyncStrategy.LOCAL_WINS -> {
                uploadFile(fileId, null).collect()
                SyncResult.Success(metadataRepo.getFileMetadata(fileId)!!)
            }

            SyncStrategy.REMOTE_WINS -> {
                downloadFile(fileId, null).collect()
                SyncResult.Success(metadataRepo.getFileMetadata(fileId)!!)
            }

            SyncStrategy.NEWEST_WINS -> {
                val remoteMetadata =
                    remoteRepo.getRemoteMetadata(fileId) ?: return SyncResult.Error(
                        fileId,
                        "Remote file not found"
                    )

                if (metadata.lastModified > remoteMetadata.lastModified) {
                    uploadFile(fileId, null).collect()
                } else {
                    downloadFile(fileId, null).collect()
                }
                SyncResult.Success(metadataRepo.getFileMetadata(fileId)!!)
            }

            else -> {
                SyncResult.Error(fileId, "Invalid resolution strategy")
            }
        }
    }

    private suspend fun isNetworkSuitable(config: SyncConfig): Boolean {
        return when (config.networkType) {
            NetworkType.ANY -> networkMonitor.isNetworkAvailable()
            NetworkType.WIFI_ONLY -> networkMonitor.isWifiAvailable()
            NetworkType.UNMETERED_ONLY -> networkMonitor.isUnmeteredNetworkAvailable()
            NetworkType.NONE -> true // Offline mode - allow operations regardless of network
        }
    }
}