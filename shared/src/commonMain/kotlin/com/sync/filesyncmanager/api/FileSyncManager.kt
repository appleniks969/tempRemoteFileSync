package com.sync.filesyncmanager.api

import com.sync.filesyncmanager.domain.BatchSyncResult
import com.sync.filesyncmanager.domain.CacheStrategy
import com.sync.filesyncmanager.domain.ConfigRepository
import com.sync.filesyncmanager.domain.FileMetadata
import com.sync.filesyncmanager.domain.FileMetadataRepository
import com.sync.filesyncmanager.domain.LocalFileRepository
import com.sync.filesyncmanager.domain.NetworkType
import com.sync.filesyncmanager.domain.PathUtils
import com.sync.filesyncmanager.domain.RemoteFileRepository
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.SyncProgress
import com.sync.filesyncmanager.domain.SyncResult
import com.sync.filesyncmanager.domain.SyncStatus
import com.sync.filesyncmanager.domain.SyncStrategy
import com.sync.filesyncmanager.domain.getPlatformFilesDir
import com.sync.filesyncmanager.util.NetworkMonitor
import com.sync.filesyncmanager.util.SyncScheduler
import com.sync.filesyncmanager.util.ZipService
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FileSyncManager {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var autoSyncJob: Job? = null

    override fun syncFile(fileId: String): Flow<SyncProgress> =
        channelFlow {
            val metadata =
                metadataRepo.getFileMetadata(fileId)
                    ?: throw IllegalArgumentException("File not found: $fileId")
            val config = configRepo.getSyncConfig()

            if (!isNetworkSuitable(config)) {
                send(
                    SyncProgress(
                        fileId = fileId,
                        fileName = metadata.fileName,
                        bytesTransferred = 0,
                        totalBytes = metadata.fileSize,
                        progress = 0f,
                        status = SyncStatus.FAILED,
                        isDownload = false,
                    ),
                )
                throw IllegalStateException("Network unavailable or unsuitable for sync")
            }

            when (config.syncStrategy) {
                SyncStrategy.DOWNLOAD_ONLY -> {
                    if (!metadata.isDownloaded) {
                        downloadFile(fileId, null).collect { send(it) }
                    }
                }

                SyncStrategy.UPLOAD_ONLY -> {
                    if (!metadata.isUploaded) {
                        uploadFile(fileId, null).collect { send(it) }
                    }
                }

                SyncStrategy.BIDIRECTIONAL, SyncStrategy.LOCAL_WINS, SyncStrategy.REMOTE_WINS, SyncStrategy.NEWEST_WINS -> {
                    val remoteMetadata = remoteRepo.getRemoteMetadata(fileId)

                    if (remoteMetadata == null) {
                        // File doesn't exist remotely, upload it
                        if (localRepo.fileExists(metadata.filePath)) {
                            uploadFile(fileId, null).collect { send(it) }
                        }
                    } else if (!metadata.isDownloaded) {
                        // Local file doesn't exist, download it
                        downloadFile(fileId, null).collect { send(it) }
                    } else {
                        // Both exist, check for conflicts
                        val localChecksum = localRepo.getFileChecksum(metadata.filePath) ?: ""
                        val remoteChecksum = remoteRepo.getFileChecksum(fileId) ?: ""

                        if (localChecksum == remoteChecksum) {
                            // Files are the same, update status
                            metadataRepo.updateSyncStatus(fileId, SyncStatus.SYNCED)
                            send(
                                SyncProgress(
                                    fileId = fileId,
                                    fileName = metadata.fileName,
                                    bytesTransferred = metadata.fileSize,
                                    totalBytes = metadata.fileSize,
                                    progress = 1f,
                                    status = SyncStatus.SYNCED,
                                    isDownload = false,
                                    filePath = metadata.filePath,
                                ),
                            )
                        } else {
                            // Conflict resolution
                            when (config.syncStrategy) {
                                SyncStrategy.LOCAL_WINS -> {
                                    uploadFile(fileId, null).collect { send(it) }
                                }

                                SyncStrategy.REMOTE_WINS -> {
                                    downloadFile(fileId, null).collect { send(it) }
                                }

                                SyncStrategy.NEWEST_WINS -> {
                                    if (metadata.lastModified > remoteMetadata.lastModified) {
                                        uploadFile(fileId, null).collect { send(it) }
                                    } else {
                                        downloadFile(fileId, null).collect { send(it) }
                                    }
                                }

                                else -> {
                                    // Mark as conflict for manual resolution
                                    metadataRepo.updateSyncStatus(fileId, SyncStatus.CONFLICT)
                                    send(
                                        SyncProgress(
                                            fileId = fileId,
                                            fileName = metadata.fileName,
                                            bytesTransferred = 0,
                                            totalBytes = metadata.fileSize,
                                            progress = 0f,
                                            status = SyncStatus.CONFLICT,
                                            isDownload = false,
                                            filePath = metadata.filePath,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }.flowOn(dispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun syncAll(): Flow<BatchSyncResult> =
        channelFlow {
            val config = configRepo.getSyncConfig()
            if (!isNetworkSuitable(config)) {
                send(BatchSyncResult(0, 0, 0, emptyList(), 0))
                return@channelFlow
            }

            val pendingFiles = metadataRepo.getUnsyncedFiles()

            // Use synchronized variables to prevent concurrent modifications
            val syncCounters = SynchronizedCounters()

            // Skip if no files to sync
            if (pendingFiles.isEmpty()) {
                send(BatchSyncResult(0, 0, 0, emptyList(), 0))
                return@channelFlow
            }

            // Send initial status
            send(
                BatchSyncResult(
                    successCount = 0,
                    failedCount = 0,
                    conflictCount = 0,
                    failedFiles = emptyList(),
                    totalProcessed = 0,
                ),
            )

            // Create a collection of sync operations to run
            val syncJobs =
                pendingFiles.map { metadata ->
                    async {
                        try {
                            syncFile(metadata.fileId).collect { progress ->
                                when (progress.status) {
                                    SyncStatus.SYNCED -> syncCounters.incrementSuccessCount()
                                    SyncStatus.CONFLICT -> syncCounters.incrementConflictCount()
                                    SyncStatus.FAILED -> {
                                        syncCounters.incrementFailedCount()
                                        syncCounters.addFailedFile(metadata.fileId, "Failed to sync")
                                    }
                                    else -> { /* Other statuses don't affect counters */ }
                                }
                            }
                        } catch (e: Exception) {
                            syncCounters.incrementFailedCount()
                            syncCounters.addFailedFile(metadata.fileId, e.message ?: "Unknown error")
                        } finally {
                            syncCounters.incrementTotalProcessed()

                            // Send progress update
                            send(
                                BatchSyncResult(
                                    successCount = syncCounters.successCount,
                                    failedCount = syncCounters.failedCount,
                                    conflictCount = syncCounters.conflictCount,
                                    failedFiles = syncCounters.failedFiles.toList(),
                                    totalProcessed = syncCounters.totalProcessed,
                                ),
                            )
                        }
                    }
                }

            // Wait for all sync operations to complete
            syncJobs.awaitAll()

            // Send final result
            send(
                BatchSyncResult(
                    successCount = syncCounters.successCount,
                    failedCount = syncCounters.failedCount,
                    conflictCount = syncCounters.conflictCount,
                    failedFiles = syncCounters.failedFiles.toList(),
                    totalProcessed = syncCounters.totalProcessed,
                ),
            )
        }.flowOn(dispatcher)

    /**
     * Thread-safe counters for tracking sync statistics
     */
    private class SynchronizedCounters {
        private val _successCount = atomic(0)
        val successCount: Int get() = _successCount.value

        private val _failedCount = atomic(0)
        val failedCount: Int get() = _failedCount.value

        private val _conflictCount = atomic(0)
        val conflictCount: Int get() = _conflictCount.value

        private val _totalProcessed = atomic(0)
        val totalProcessed: Int get() = _totalProcessed.value

        private val failedFilesList = mutableListOf<Pair<String, String>>()
        private val failedFilesLock = Any()
        val failedFiles: List<Pair<String, String>>
            get() = failedFilesList.toList()

        fun incrementSuccessCount() {
            _successCount.incrementAndGet()
        }

        fun incrementFailedCount() {
            _failedCount.incrementAndGet()
        }

        fun incrementConflictCount() {
            _conflictCount.incrementAndGet()
        }

        fun incrementTotalProcessed() {
            _totalProcessed.incrementAndGet()
        }

        fun addFailedFile(
            fileId: String,
            error: String,
        ) {
            failedFilesList.add(fileId to error)
        }
    }

    override fun downloadFile(
        fileId: String,
        destinationPath: String?,
    ): Flow<SyncProgress> =
        channelFlow {
            val metadata =
                metadataRepo.getFileMetadata(fileId)
                    ?: throw IllegalArgumentException("File not found: $fileId")

            // If no destination path provided, use existing metadata path or generate a new one
            val targetPath =
                if (destinationPath != null) {
                    destinationPath
                } else if (metadata.filePath.isNotEmpty()) {
                    metadata.filePath
                } else {
                    // Generate a default path if none exists
                    PathUtils.combine(
                        getPlatformFilesDir(),
                        "downloads",
                        metadata.fileName.ifEmpty { "file_$fileId" },
                    )
                }

            // Create parent directory if it doesn't exist
            val parentDir = targetPath.substringBeforeLast('/', "")
            if (parentDir.isNotEmpty() && !localRepo.fileExists(parentDir)) {
                localRepo.createDirectory(parentDir)
            }

            metadataRepo.updateSyncStatus(fileId, SyncStatus.DOWNLOADING)

            remoteRepo
                .downloadFile(fileId, targetPath)
                .collect { progress ->
                    send(progress)

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
                        val updatedMetadata =
                            metadata.copy(
                                filePath = targetPath, // Update path in case it was generated
                                isZipFile = isZip,
                                extractedPath = extractedPath,
                                isExtracted = isExtracted,
                            )

                        // Save the updated metadata
                        metadataRepo.saveFileMetadata(updatedMetadata)

                        // Update download status
                        metadataRepo.updateDownloadStatus(
                            fileId = fileId,
                            isDownloaded = true,
                            checksum = checksum,
                            status = SyncStatus.SYNCED,
                        )

                        // Emit final progress with either the extracted path or original path
                        if (isExtracted) {
                            send(progress.copy(filePath = extractedPath))
                        }
                    }
                }
        }.flowOn(dispatcher)

    override fun uploadFile(
        fileId: String,
        localPath: String?,
    ): Flow<SyncProgress> =
        channelFlow {
            val metadata =
                metadataRepo.getFileMetadata(fileId)
                    ?: throw IllegalArgumentException("File not found: $fileId")
            val sourcePath = localPath ?: metadata.filePath

            if (!localRepo.fileExists(sourcePath)) {
                throw IllegalArgumentException("Local file does not exist: $sourcePath")
            }

            metadataRepo.updateSyncStatus(fileId, SyncStatus.UPLOADING)

            remoteRepo
                .uploadFile(fileId, sourcePath)
                .collect { progress ->
                    send(progress)

                    if (progress.progress >= 1.0f) {
                        val remoteChecksum = remoteRepo.getFileChecksum(fileId)
                        metadataRepo.updateUploadStatus(
                            fileId = fileId,
                            isUploaded = true,
                            checksum = remoteChecksum,
                            status = SyncStatus.SYNCED,
                        )

                        // If we used a custom local path, update the metadata
                        if (localPath != null && localPath != metadata.filePath) {
                            val updatedMetadata = metadata.copy(filePath = localPath)
                            metadataRepo.saveFileMetadata(updatedMetadata)
                        }
                    }
                }
        }.flowOn(dispatcher)

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
                                filePath = metadata.filePath,
                            ),
                        )
                    }.collect()
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
        return try {
            val metadata =
                metadataRepo.getFileMetadata(fileId) ?: return SyncResult.Error(
                    fileId,
                    "File not found",
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
        autoSyncJob?.cancel()

        autoSyncJob =
            scope.launch {
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
                            status = SyncStatus.PENDING,
                        )
                    }
                }
                currentSize
            }

            CacheStrategy.CACHE_RECENT -> {
                // Keep only recently accessed files
                val now = Clock.System.now()
                val filesToDelete =
                    metadataRepo.getAllFileMetadata().filter { metadata ->
                        metadata.isDownloaded &&
                            metadata.lastSyncTime != null &&
                            (
                                (now.toEpochMilliseconds() - metadata.lastSyncTime.toEpochMilliseconds()) >
                                    (config.fileExpiryDuration ?: Long.MAX_VALUE)
                            )
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
                            status = SyncStatus.PENDING,
                        )
                    }
                }
                bytesCleared
            }

            CacheStrategy.CACHE_PRIORITY -> {
                // Delete low priority files if over size limit
                if (config.maxCacheSize != null && currentSize > config.maxCacheSize) {
                    val exceedingBytes = currentSize - config.maxCacheSize
                    val filesToDelete =
                        metadataRepo
                            .getAllFileMetadata()
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
                                status = SyncStatus.PENDING,
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

    override suspend fun resolveConflict(
        fileId: String,
        resolution: SyncStrategy,
    ): SyncResult {
        val metadata =
            metadataRepo.getFileMetadata(fileId) ?: return SyncResult.Error(
                fileId,
                "File not found",
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
                        "Remote file not found",
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

    private suspend fun isNetworkSuitable(config: SyncConfig): Boolean =
        when (config.networkType) {
            NetworkType.ANY -> networkMonitor.isNetworkAvailable()
            NetworkType.WIFI_ONLY -> networkMonitor.isWifiAvailable()
            NetworkType.UNMETERED_ONLY -> networkMonitor.isUnmeteredNetworkAvailable()
            NetworkType.NONE -> true // Offline mode - allow operations regardless of network
        }
}
