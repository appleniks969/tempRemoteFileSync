package com.sync.filesyncmanager.domain

import com.sync.filesyncmanager.data.local.FileSyncDatabase
import com.sync.filesyncmanager.util.FileService
import com.sync.filesyncmanager.util.getPlatformCacheDir
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Cross-platform interface for accessing preferences storage
 */
interface PreferenceStorage {
    suspend fun getSyncConfig(): SyncConfig
    suspend fun updateSyncConfig(config: SyncConfig)
    fun observeSyncConfig(): Flow<SyncConfig>
}

/**
 * Repository for configuration management
 */
class ConfigRepository(private val preferenceStorage: PreferenceStorage) {

    /**
     * Gets the current sync configuration
     */
    suspend fun getSyncConfig(): SyncConfig {
        return preferenceStorage.getSyncConfig()
    }

    /**
     * Updates the sync configuration
     */
    suspend fun updateSyncConfig(config: SyncConfig) {
        preferenceStorage.updateSyncConfig(config)
    }

    /**
     * Observes changes to the sync configuration
     */
    fun observeSyncConfig(): Flow<SyncConfig> {
        return preferenceStorage.observeSyncConfig()
    }

    /**
     * Resets the configuration to default values
     */
    suspend fun resetToDefaults() {
        updateSyncConfig(getDefaultConfig())
    }

    /**
     * Returns the default configuration
     */
    private fun getDefaultConfig(): SyncConfig {
        return SyncConfig(
            baseUrl = "",
            syncStrategy = SyncStrategy.BIDIRECTIONAL,
            cacheStrategy = CacheStrategy.CACHE_RECENT,
            maxConcurrentTransfers = 3,
            autoSyncInterval = null,
            networkType = NetworkType.ANY,
            syncOnlyOnWifi = false,
            authToken = null,
            compressionEnabled = true,
            unzipFiles = false,
            deleteZipAfterExtract = false,
            retryCount = 3,
            retryDelay = 5000L,
            maxCacheSize = null,
            fileExpiryDuration = null
        )
    }
}

/**
 * Repository for file metadata management
 */
class FileMetadataRepository(private val database: FileSyncDatabase) {
    private val dao = database.fileMetadataDao()

    /**
     * Gets file metadata by ID
     */
    suspend fun getFileMetadata(fileId: String): FileMetadata? {
        return dao.getById(fileId)
    }

    /**
     * Gets file metadata by file path
     */
    suspend fun getFileMetadataByPath(path: String): FileMetadata? {
        return dao.getByPath(path)
    }

    /**
     * Gets all file metadata
     */
    suspend fun getAllFileMetadata(): List<FileMetadata> {
        return dao.getAll()
    }

    /**
     * Gets unsynced files
     */
    suspend fun getUnsyncedFiles(): List<FileMetadata> {
        return dao.getUnsyncedFiles()
    }

    /**
     * Gets pending downloads
     */
    suspend fun getPendingDownloads(): List<FileMetadata> {
        return dao.getPendingDownloads()
    }

    /**
     * Gets pending uploads
     */
    suspend fun getPendingUploads(): List<FileMetadata> {
        return dao.getPendingUploads()
    }

    /**
     * Observes file metadata by ID
     */
    fun observeFileMetadata(fileId: String): Flow<FileMetadata?> {
        return dao.observeById(fileId)
    }

    /**
     * Observes all file metadata
     */
    fun observeAllFileMetadata(): Flow<List<FileMetadata>> {
        return dao.observeAll()
    }

    /**
     * Saves file metadata
     */
    suspend fun saveFileMetadata(metadata: FileMetadata) {
        dao.insert(metadata)
    }

    /**
     * Updates sync status
     */
    suspend fun updateSyncStatus(fileId: String, status: SyncStatus) {
        val now = Clock.System.now()
        dao.updateSyncStatus(fileId, status, now)
    }

    /**
     * Updates download status
     */
    suspend fun updateDownloadStatus(
        fileId: String,
        isDownloaded: Boolean,
        checksum: String?,
        status: SyncStatus
    ) {
        val now = Clock.System.now()
        dao.updateDownloadStatus(fileId, isDownloaded, checksum, status, now)
    }

    /**
     * Updates upload status
     */
    suspend fun updateUploadStatus(
        fileId: String,
        isUploaded: Boolean,
        checksum: String?,
        status: SyncStatus
    ) {
        val now = Clock.System.now()
        dao.updateUploadStatus(fileId, isUploaded, checksum, status, now)
    }

    /**
     * Deletes file metadata
     */
    suspend fun deleteFileMetadata(fileId: String) {
        dao.delete(fileId)
    }

    /**
     * Marks file as deleted
     */
    suspend fun markFileAsDeleted(fileId: String) {
        dao.markAsDeleted(fileId)
    }

    /**
     * Clears all metadata
     */
    suspend fun clearAllMetadata() {
        dao.deleteAll()
    }
}


/**
 * Implementation of LocalFileRepository using FileService
 */
class LocalFileRepository(private val fileService: FileService) {

    /**
     * Saves a file to the local storage
     */
    suspend fun saveFile(fileId: String, filePath: String, data: ByteArray): Boolean {
        return fileService.writeFile(filePath, data)
    }

    /**
     * Reads a file from local storage
     */
    suspend fun readFile(fileId: String, filePath: String): ByteArray? {
        return fileService.readFile(filePath)
    }

    /**
     * Deletes a file from local storage
     */
    suspend fun deleteFile(filePath: String): Boolean {
        return fileService.deleteFile(filePath)
    }

    /**
     * Checks if a file exists at the specified path
     */
    suspend fun fileExists(filePath: String): Boolean {
        return fileService.fileExists(filePath)
    }

    /**
     * Gets the checksum of a file
     */
    suspend fun getFileChecksum(filePath: String): String? {
        return fileService.calculateChecksum(filePath)
    }

    /**
     * Creates a directory
     */
    suspend fun createDirectory(dirPath: String): Boolean {
        return fileService.createDirectory(dirPath)
    }

    /**
     * Gets the size of a file
     */
    suspend fun getFileSize(filePath: String): Long {
        return fileService.getFileSize(filePath)
    }

    /**
     * Gets the last modified timestamp of a file
     */
    suspend fun getFileLastModified(filePath: String): Instant {
        return fileService.getLastModified(filePath) ?: Instant.fromEpochMilliseconds(0)
    }

    /**
     * Lists files in a directory
     */
    suspend fun listFiles(dirPath: String): List<String> {
        return fileService.listFiles(dirPath)
    }

    /**
     * Moves a file from one path to another
     */
    suspend fun moveFile(sourcePath: String, destinationPath: String): Boolean {
        return fileService.moveFile(sourcePath, destinationPath)
    }

    /**
     * Copies a file from one path to another
     */
    suspend fun copyFile(sourcePath: String, destinationPath: String): Boolean {
        return fileService.copyFile(sourcePath, destinationPath)
    }

    /**
     * Gets the total size of the cache directory
     */
    suspend fun getTotalCacheSize(): Long {
        return fileService.getTotalDirectorySize(getPlatformCacheDir())
    }

    /**
     * Clears a directory by deleting all files
     */
    suspend fun clearCache(dirPath: String): Boolean {
        return fileService.clearDirectory(dirPath)
    }
}



/**
 * Implementation of RemoteFileRepository using HTTP client
 */
class RemoteFileRepository(
    private val fileService: FileService,
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val authToken: String?
) {

    @Serializable
    private data class RemoteFileMetadataDto(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val lastModified: Long,
        val checksum: String? = null
    )

    @Serializable
    private data class RemoteFileListResponse(
        val files: List<RemoteFileMetadataDto>
    )

    /**
     * Downloads a file from the remote server
     */
    fun downloadFile(fileId: String, destinationPath: String): Flow<SyncProgress> =
        flow {
            try {
                val fileInfo = getRemoteMetadata(fileId)
                    ?: throw IllegalArgumentException("File not found: $fileId")

                emit(
                    SyncProgress(
                        fileId = fileId,
                        fileName = fileInfo.fileName,
                        bytesTransferred = 0,
                        totalBytes = fileInfo.fileSize,
                        progress = 0f,
                        status = SyncStatus.DOWNLOADING,
                        isDownload = true,
                        filePath = destinationPath
                    )
                )

                val response = httpClient.get("$baseUrl/files/$fileId/content") {
                    headers {
                        authToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                    }
                }

                if (response.status.isSuccess()) {

                    // Create parent directory
                    val lastSlashIndex = destinationPath.lastIndexOf('/')
                    if (lastSlashIndex > 0) {
                        val dirPath = destinationPath.substring(0, lastSlashIndex)
                        fileService.createDirectory(dirPath)
                    }

                    val channel: ByteReadChannel = response.bodyAsChannel()
                    val fileSize = fileInfo.fileSize
                    var bytesReceived = 0L
                    val buffer = ByteArray(8192)

                    // Create a temporary file for streaming
                    val tempPath = "$destinationPath.temp"

                    // Open the file for writing
                    val outputData = mutableListOf<ByteArray>()

                    while (true) {
                        val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                        if (bytesRead <= 0) break

                        // Copy to a new buffer of exact size to avoid including junk
                        val exactBuffer = buffer.copyOfRange(0, bytesRead)
                        outputData.add(exactBuffer)

                        bytesReceived += bytesRead
                        val progress = if (fileSize > 0) bytesReceived.toFloat() / fileSize else 0f

                        emit(
                            SyncProgress(
                                fileId = fileId,
                                fileName = fileInfo.fileName,
                                bytesTransferred = bytesReceived,
                                totalBytes = fileSize,
                                progress = progress,
                                status = SyncStatus.DOWNLOADING,
                                isDownload = true,
                                filePath = destinationPath
                            )
                        )
                    }

                    // Combine all chunks and write to file
                    val totalSize = outputData.sumOf { it.size }
                    val combinedData = ByteArray(totalSize)
                    var position = 0

                    for (chunk in outputData) {
                        chunk.copyInto(combinedData, position)
                        position += chunk.size
                    }

                    // Write the data to the final file
                    fileService.writeFile(destinationPath, combinedData)

                    emit(
                        SyncProgress(
                            fileId = fileId,
                            fileName = fileInfo.fileName,
                            bytesTransferred = fileSize,
                            totalBytes = fileSize,
                            progress = 1f,
                            status = SyncStatus.SYNCED,
                            isDownload = true,
                            filePath = destinationPath
                        )
                    )
                } else {
                    throw Exception("Failed to download file: ${response.status}")
                }
            } catch (e: Exception) {
                emit(
                    SyncProgress(
                        fileId = fileId,
                        fileName = fileId, // Fallback since we don't have the name
                        bytesTransferred = 0,
                        totalBytes = 0,
                        progress = 0f,
                        status = SyncStatus.FAILED,
                        isDownload = true,
                        filePath = destinationPath
                    )
                )
                throw e
            }
        }

    /**
     * Uploads a file to the remote server
     */
    fun uploadFile(fileId: String, localPath: String): Flow<SyncProgress> = flow {
        try {

            if (!fileService.fileExists(localPath)) {
                throw IllegalArgumentException("Local file does not exist: $localPath")
            }

            val fileSize = fileService.getFileSize(localPath)
            val fileName = localPath.substringAfterLast('/')

            emit(
                SyncProgress(
                    fileId = fileId,
                    fileName = fileName,
                    bytesTransferred = 0,
                    totalBytes = fileSize,
                    progress = 0f,
                    status = SyncStatus.UPLOADING,
                    isDownload = false,
                    filePath = localPath
                )
            )

            // Read the file data
            val fileData = fileService.readFile(localPath)
                ?: throw IllegalArgumentException("Could not read file: $localPath")

            // Upload the file
            val response = httpClient.post("$baseUrl/files/$fileId/content") {
                headers {
                    authToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
                setBody(fileData)
            }

            if (response.status.isSuccess()) {
                emit(
                    SyncProgress(
                        fileId = fileId,
                        fileName = fileName,
                        bytesTransferred = fileSize,
                        totalBytes = fileSize,
                        progress = 1f,
                        status = SyncStatus.SYNCED,
                        isDownload = false,
                        filePath = localPath
                    )
                )
            } else {
                throw Exception("Failed to upload file: ${response.status}")
            }
        } catch (e: Exception) {
            val fileName = localPath.substringAfterLast('/')
            emit(
                SyncProgress(
                    fileId = fileId,
                    fileName = fileName,
                    bytesTransferred = 0,
                    totalBytes = 0,
                    progress = 0f,
                    status = SyncStatus.FAILED,
                    isDownload = false,
                    filePath = localPath
                )
            )
            throw e
        }
    }

    /**
     * Gets the metadata of a remote file
     */
    suspend fun getRemoteMetadata(fileId: String): FileMetadata? {
        return try {
            val response = httpClient.get("$baseUrl/files/$fileId") {
                headers {
                    authToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
            }

            if (response.status.isSuccess()) {
                val dto: RemoteFileMetadataDto = response.body()

                // Convert DTO to domain model
                FileMetadata(
                    fileId = dto.fileId,
                    fileName = dto.fileName,
                    filePath = "", // Remote file doesn't have a local path yet
                    remoteUrl = "$baseUrl/files/${dto.fileId}/content",
                    lastModified = Instant.fromEpochMilliseconds(dto.lastModified),
                    fileSize = dto.fileSize,
                    syncStatus = SyncStatus.PENDING,
                    remoteChecksum = dto.checksum,
                    isUploaded = true
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets a list of all remote files
     */
    suspend fun getRemoteFilesList(): List<FileMetadata> {
        return try {
            val response = httpClient.get("$baseUrl/files") {
                headers {
                    authToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
            }

            if (response.status.isSuccess()) {
                val listResponse: RemoteFileListResponse = response.body()

                listResponse.files.map { dto ->
                    FileMetadata(
                        fileId = dto.fileId,
                        fileName = dto.fileName,
                        filePath = "", // Remote file doesn't have a local path yet
                        remoteUrl = "$baseUrl/files/${dto.fileId}/content",
                        lastModified = Instant.fromEpochMilliseconds(dto.lastModified),
                        fileSize = dto.fileSize,
                        syncStatus = SyncStatus.PENDING,
                        remoteChecksum = dto.checksum,
                        isUploaded = true
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Deletes a remote file
     */
    suspend fun deleteRemoteFile(fileId: String): Boolean {
        return try {
            val response = httpClient.delete("$baseUrl/files/$fileId") {
                headers {
                    authToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
            }

            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a remote file exists
     */
    suspend fun checkFileExists(fileId: String): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/files/$fileId") {
                headers {
                    authToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
            }

            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the checksum of a remote file
     */
    suspend fun getFileChecksum(fileId: String): String? {
        return try {
            val response = httpClient.get("$baseUrl/files/$fileId/checksum") {
                headers {
                    authToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
            }

            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}