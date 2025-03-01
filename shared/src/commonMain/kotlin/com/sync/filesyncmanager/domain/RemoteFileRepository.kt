package com.sync.filesyncmanager.domain

import com.sync.filesyncmanager.util.FileService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
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
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Implementation of RemoteFileRepository using Ktor HTTP client
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
                    timeout {
                        requestTimeoutMillis = 30000 // 30 seconds
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

            // Build a multipart request
            val response = httpClient.post("$baseUrl/files/$fileId/content") {
                headers {
                    authToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
                timeout {
                    requestTimeoutMillis = 60000 // 60 seconds for upload
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

    suspend fun checkFileExists(fileId: String): Boolean {
        return try {
            val response = httpClient.head("$baseUrl/files/$fileId") {
                headers {
                    authToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
            }

            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

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