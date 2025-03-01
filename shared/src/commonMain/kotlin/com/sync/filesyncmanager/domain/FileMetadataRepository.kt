package com.sync.filesyncmanager.domain

import com.sync.filesyncmanager.data.local.FileMetadataDao
import com.sync.filesyncmanager.data.local.FileSyncDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Repository for managing file metadata with platform-specific implementations
 */
class FileMetadataRepository(private val database: Any) {

    // In-memory map for iOS or when Room is not available
    private val inMemoryData = mutableMapOf<String, FileMetadata>()
    private val inMemoryFlow = MutableStateFlow<Map<String, FileMetadata>>(emptyMap())

    /**
     * Gets file metadata by ID
     */
    suspend fun getFileMetadata(fileId: String): FileMetadata? {
        return when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.getById(fileId)
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData[fileId]
            }
        }
    }

    /**
     * Gets file metadata by file path
     */
    suspend fun getFileMetadataByPath(path: String): FileMetadata? {
        return when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.getByPath(path)
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData.values.find { it.filePath == path }
            }
        }
    }

    /**
     * Gets all file metadata
     */
    suspend fun getAllFileMetadata(): List<FileMetadata> {
        return when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.getAll()
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData.values.filter { !it.isDeleted }.toList()
            }
        }
    }

    /**
     * Gets unsynced files
     */
    suspend fun getUnsyncedFiles(): List<FileMetadata> {
        return when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.getUnsyncedFiles()
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData.values.filter { 
                    !it.isDeleted && it.syncStatus != SyncStatus.SYNCED 
                }.toList()
            }
        }
    }

    /**
     * Gets pending downloads
     */
    suspend fun getPendingDownloads(): List<FileMetadata> {
        return when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.getPendingDownloads()
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData.values.filter { 
                    !it.isDeleted && !it.isDownloaded 
                }.toList()
            }
        }
    }

    /**
     * Gets pending uploads
     */
    suspend fun getPendingUploads(): List<FileMetadata> {
        return when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.getPendingUploads()
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData.values.filter { 
                    !it.isDeleted && !it.isUploaded 
                }.toList()
            }
        }
    }

    /**
     * Observes file metadata by ID
     */
    fun observeFileMetadata(fileId: String): Flow<FileMetadata?> {
        return when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.observeById(fileId)
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryFlow.map { it[fileId] }
            }
        }
    }

    /**
     * Observes all file metadata
     */
    fun observeAllFileMetadata(): Flow<List<FileMetadata>> {
        return when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.observeAll()
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryFlow.map { it.values.filter { metadata -> !metadata.isDeleted }.toList() }
            }
        }
    }

    /**
     * Saves file metadata
     */
    suspend fun saveFileMetadata(metadata: FileMetadata) {
        when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.insert(metadata)
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData[metadata.fileId] = metadata
                inMemoryFlow.value = inMemoryData.toMap()
            }
        }
    }

    /**
     * Updates sync status
     */
    suspend fun updateSyncStatus(fileId: String, status: SyncStatus) {
        val now = Clock.System.now()
        
        when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.updateSyncStatus(fileId, status, now)
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData[fileId]?.let {
                    inMemoryData[fileId] = it.copy(
                        syncStatus = status,
                        lastSyncTime = now
                    )
                    inMemoryFlow.value = inMemoryData.toMap()
                }
            }
        }
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
        
        when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.updateDownloadStatus(fileId, isDownloaded, checksum, status, now)
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData[fileId]?.let {
                    inMemoryData[fileId] = it.copy(
                        isDownloaded = isDownloaded,
                        localChecksum = checksum,
                        syncStatus = status,
                        lastSyncTime = now
                    )
                    inMemoryFlow.value = inMemoryData.toMap()
                }
            }
        }
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
        
        when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.updateUploadStatus(fileId, isUploaded, checksum, status, now)
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData[fileId]?.let {
                    inMemoryData[fileId] = it.copy(
                        isUploaded = isUploaded,
                        remoteChecksum = checksum,
                        syncStatus = status,
                        lastSyncTime = now
                    )
                    inMemoryFlow.value = inMemoryData.toMap()
                }
            }
        }
    }

    /**
     * Deletes file metadata
     */
    suspend fun deleteFileMetadata(fileId: String) {
        when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.delete(fileId)
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData.remove(fileId)
                inMemoryFlow.value = inMemoryData.toMap()
            }
        }
    }

    /**
     * Marks file as deleted
     */
    suspend fun markFileAsDeleted(fileId: String) {
        when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.markAsDeleted(fileId)
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData[fileId]?.let {
                    inMemoryData[fileId] = it.copy(isDeleted = true)
                    inMemoryFlow.value = inMemoryData.toMap()
                }
            }
        }
    }

    /**
     * Clears all metadata
     */
    suspend fun clearAllMetadata() {
        when (database) {
            is FileSyncDatabase -> {
                val dao = database.fileMetadataDao()
                dao.deleteAll()
            }
            else -> {
                // For iOS or in-memory storage
                inMemoryData.clear()
                inMemoryFlow.value = emptyMap()
            }
        }
    }
}