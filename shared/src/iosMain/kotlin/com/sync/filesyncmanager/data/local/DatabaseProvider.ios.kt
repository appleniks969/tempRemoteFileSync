package com.sync.filesyncmanager.data.local

import com.sync.filesyncmanager.domain.FileMetadata
import com.sync.filesyncmanager.domain.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * iOS implementation of DatabaseProvider using a simple in-memory database
 */
actual object DatabaseProvider {
    // We're using a simpler mock implementation for iOS that doesn't depend on Room
    // but still provides the expected API interface
    private var instance: IosFileSyncDatabase? = null

    /**
     * Creates or returns a simple in-memory database instance for iOS
     */
    actual fun getDatabase(): IFileSyncDatabase {
        if (instance == null) {
            instance = IosFileSyncDatabase()
        }
        return instance!!
    }
}

/**
 * iOS-specific implementation of IFileSyncDatabase
 * that doesn't rely on Room but provides the same API
 */
class IosFileSyncDatabase : IFileSyncDatabase {
    // Use a state flow to store and observe data
    private val storage = MutableStateFlow<Map<String, FileMetadata>>(emptyMap())
    private val dao = MockFileMetadataDao(storage)

    override fun fileMetadataDao(): FileMetadataDao = dao
}

/**
 * Mock implementation of FileMetadataDao that uses a MutableStateFlow
 * to store and retrieve data
 */
class MockFileMetadataDao(
    private val storage: MutableStateFlow<Map<String, FileMetadata>>,
) : FileMetadataDao {
    override suspend fun getById(fileId: String): FileMetadata? = storage.value[fileId]

    override suspend fun getByPath(path: String): FileMetadata? = storage.value.values.firstOrNull { it.filePath == path }

    override suspend fun getAll(): List<FileMetadata> =
        storage.value.values
            .filter { !it.isDeleted }
            .toList()

    override suspend fun getUnsyncedFiles(): List<FileMetadata> =
        storage.value.values
            .filter {
                !it.isDeleted && it.syncStatus != SyncStatus.SYNCED
            }.toList()

    override suspend fun getPendingDownloads(): List<FileMetadata> =
        storage.value.values
            .filter { !it.isDeleted && !it.isDownloaded }
            .toList()

    override suspend fun getPendingUploads(): List<FileMetadata> =
        storage.value.values
            .filter { !it.isDeleted && !it.isUploaded }
            .toList()

    override fun observeById(fileId: String): Flow<FileMetadata?> = storage.map { it[fileId] }

    override fun observeAll(): Flow<List<FileMetadata>> = storage.map { it.values.filter { metadata -> !metadata.isDeleted }.toList() }

    override suspend fun insert(metadata: FileMetadata) {
        val currentMap = storage.value.toMutableMap()
        currentMap[metadata.fileId] = metadata
        storage.value = currentMap
    }

    override suspend fun updateSyncStatus(
        fileId: String,
        status: SyncStatus,
        lastSyncTime: Instant,
    ) {
        val currentMap = storage.value.toMutableMap()
        val existing = currentMap[fileId] ?: return
        currentMap[fileId] = existing.copy(syncStatus = status, lastSyncTime = lastSyncTime)
        storage.value = currentMap
    }

    override suspend fun updateDownloadStatus(
        fileId: String,
        isDownloaded: Boolean,
        checksum: String?,
        status: SyncStatus,
        lastSyncTime: Instant,
    ) {
        val currentMap = storage.value.toMutableMap()
        val existing = currentMap[fileId] ?: return
        currentMap[fileId] =
            existing.copy(
                isDownloaded = isDownloaded,
                localChecksum = checksum,
                syncStatus = status,
                lastSyncTime = lastSyncTime,
            )
        storage.value = currentMap
    }

    override suspend fun updateUploadStatus(
        fileId: String,
        isUploaded: Boolean,
        checksum: String?,
        status: SyncStatus,
        lastSyncTime: Instant,
    ) {
        val currentMap = storage.value.toMutableMap()
        val existing = currentMap[fileId] ?: return
        currentMap[fileId] =
            existing.copy(
                isUploaded = isUploaded,
                remoteChecksum = checksum,
                syncStatus = status,
                lastSyncTime = lastSyncTime,
            )
        storage.value = currentMap
    }

    override suspend fun delete(fileId: String) {
        val currentMap = storage.value.toMutableMap()
        currentMap.remove(fileId)
        storage.value = currentMap
    }

    override suspend fun markAsDeleted(fileId: String) {
        val currentMap = storage.value.toMutableMap()
        val existing = currentMap[fileId] ?: return
        currentMap[fileId] = existing.copy(isDeleted = true)
        storage.value = currentMap
    }

    override suspend fun deleteAll() {
        storage.value = emptyMap()
    }
}
