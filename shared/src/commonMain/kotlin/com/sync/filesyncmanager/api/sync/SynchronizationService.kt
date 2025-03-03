package com.sync.filesyncmanager.api.sync

import com.sync.filesyncmanager.api.network.NetworkManager
import com.sync.filesyncmanager.domain.BatchSyncResult
import com.sync.filesyncmanager.domain.ConfigRepository
import com.sync.filesyncmanager.domain.FileMetadata
import com.sync.filesyncmanager.domain.FileMetadataRepository
import com.sync.filesyncmanager.domain.LocalFileRepository
import com.sync.filesyncmanager.domain.PathUtils
import com.sync.filesyncmanager.domain.RemoteFileRepository
import com.sync.filesyncmanager.domain.SyncProgress
import com.sync.filesyncmanager.domain.SyncResult
import com.sync.filesyncmanager.domain.SyncStatus
import com.sync.filesyncmanager.domain.SyncStrategy
import com.sync.filesyncmanager.domain.getPlatformFilesDir
import com.sync.filesyncmanager.util.ZipService
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn

/**
 * Service responsible for file synchronization operations
 */
interface SynchronizationService {
    /**
     * Synchronizes a specific file
     */
    fun syncFile(fileId: String): Flow<SyncProgress>

    /**
     * Synchronizes all files
     */
    fun syncAll(): Flow<BatchSyncResult>

    /**
     * Downloads a specific file
     */
    fun downloadFile(
        fileId: String,
        destinationPath: String?,
    ): Flow<SyncProgress>

    /**
     * Uploads a specific file
     */
    fun uploadFile(
        fileId: String,
        localPath: String?,
    ): Flow<SyncProgress>

    /**
     * Resolves a conflict using the specified strategy
     */
    suspend fun resolveConflict(
        fileId: String,
        resolution: SyncStrategy,
    ): SyncResult
}

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

/**
 * Implementation of SynchronizationService
 */
class SynchronizationServiceImpl(
    private val metadataRepo: FileMetadataRepository,
    private val remoteRepo: RemoteFileRepository,
    private val localRepo: LocalFileRepository,
    private val configRepo: ConfigRepository,
    private val networkManager: NetworkManager,
    private val zipService: ZipService,
    private val dispatcher: CoroutineDispatcher,
) : SynchronizationService {
    override fun syncFile(fileId: String): Flow<SyncProgress> =
        channelFlow {
            val metadata =
                metadataRepo.getFileMetadata(fileId)
                    ?: throw IllegalArgumentException("File not found: $fileId")
            val config = configRepo.getSyncConfig()

            if (!networkManager.isNetworkSuitable(config)) {
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
                    synchronizeBidirectional(fileId, metadata, config).collect { send(it) }
                }
            }
        }.flowOn(dispatcher)

    private fun synchronizeBidirectional(
        fileId: String,
        metadata: FileMetadata,
        config: com.sync.filesyncmanager.domain.SyncConfig,
    ): Flow<SyncProgress> =
        channelFlow {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun syncAll(): Flow<BatchSyncResult> =
        channelFlow {
            val config = configRepo.getSyncConfig()
            if (!networkManager.isNetworkSuitable(config)) {
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
                        handleZipExtraction(metadata, targetPath, config, checksum, progress).let {
                            if (it != null) send(it)
                        }
                    }
                }
        }.flowOn(dispatcher)

    private suspend fun handleZipExtraction(
        metadata: FileMetadata,
        targetPath: String,
        config: com.sync.filesyncmanager.domain.SyncConfig,
        checksum: String?,
        progress: SyncProgress,
    ): SyncProgress? {
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
            fileId = metadata.fileId,
            isDownloaded = true,
            checksum = checksum,
            status = SyncStatus.SYNCED,
        )

        // Return final progress with either the extracted path or original path
        return if (isExtracted) {
            progress.copy(filePath = extractedPath)
        } else {
            null
        }
    }

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
}
