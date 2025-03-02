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
    private var instance: FileSyncDatabase? = null

    /**
     * Creates or returns a simple in-memory database instance for iOS
     */
    actual fun getDatabase(): FileSyncDatabase {
        return instance ?: synchronized(this) {
            val db = IosFileSyncDatabase()
            instance = db
            db
        }
    }
}

/**
 * Simple in-memory implementation of FileSyncDatabase for iOS
 */
class IosFileSyncDatabase : FileSyncDatabase() {
    private val inMemoryDb = MutableStateFlow<Map<String, FileMetadata>>(emptyMap())
    private val dao = IosFileMetadataDao(inMemoryDb)
    
    override fun fileMetadataDao(): FileMetadataDao = dao
}

/**
 * Simple in-memory implementation of FileMetadataDao for iOS
 */
class IosFileMetadataDao(private val db: MutableStateFlow<Map<String, FileMetadata>>) : FileMetadataDao {
    
    override suspend fun getById(fileId: String): FileMetadata? {
        return db.value[fileId]
    }
    
    override suspend fun getByPath(path: String): FileMetadata? {
        return db.value.values.firstOrNull { it.filePath == path }
    }
    
    override suspend fun getAll(): List<FileMetadata> {
        return db.value.values.filter { !it.isDeleted }.toList()
    }
    
    override suspend fun getUnsyncedFiles(): List<FileMetadata> {
        return db.value.values.filter { !it.isDeleted && it.syncStatus != SyncStatus.SYNCED }.toList()
    }
    
    override suspend fun getPendingDownloads(): List<FileMetadata> {
        return db.value.values.filter { !it.isDeleted && !it.isDownloaded }.toList()
    }
    
    override suspend fun getPendingUploads(): List<FileMetadata> {
        return db.value.values.filter { !it.isDeleted && !it.isUploaded }.toList()
    }
    
    override fun observeById(fileId: String): Flow<FileMetadata?> {
        return db.map { it[fileId] }
    }
    
    override fun observeAll(): Flow<List<FileMetadata>> {
        return db.map { it.values.filter { metadata -> !metadata.isDeleted }.toList() }
    }
    
    override suspend fun insert(metadata: FileMetadata) {
        val currentMap = db.value.toMutableMap()
        currentMap[metadata.fileId] = metadata
        db.value = currentMap
    }
    
    override suspend fun updateSyncStatus(fileId: String, status: SyncStatus, lastSyncTime: Instant) {
        val currentMap = db.value.toMutableMap()
        val existing = currentMap[fileId] ?: return
        currentMap[fileId] = existing.copy(syncStatus = status, lastSyncTime = lastSyncTime)
        db.value = currentMap
    }
    
    override suspend fun updateDownloadStatus(fileId: String, isDownloaded: Boolean, checksum: String?, status: SyncStatus, lastSyncTime: Instant) {
        val currentMap = db.value.toMutableMap()
        val existing = currentMap[fileId] ?: return
        currentMap[fileId] = existing.copy(
            isDownloaded = isDownloaded,
            localChecksum = checksum,
            syncStatus = status,
            lastSyncTime = lastSyncTime
        )
        db.value = currentMap
    }
    
    override suspend fun updateUploadStatus(fileId: String, isUploaded: Boolean, checksum: String?, status: SyncStatus, lastSyncTime: Instant) {
        val currentMap = db.value.toMutableMap()
        val existing = currentMap[fileId] ?: return
        currentMap[fileId] = existing.copy(
            isUploaded = isUploaded,
            remoteChecksum = checksum,
            syncStatus = status,
            lastSyncTime = lastSyncTime
        )
        db.value = currentMap
    }
    
    override suspend fun delete(fileId: String) {
        val currentMap = db.value.toMutableMap()
        currentMap.remove(fileId)
        db.value = currentMap
    }
    
    override suspend fun markAsDeleted(fileId: String) {
        val currentMap = db.value.toMutableMap()
        val existing = currentMap[fileId] ?: return
        currentMap[fileId] = existing.copy(isDeleted = true)
        db.value = currentMap
    }
    
    override suspend fun deleteAll() {
        db.value = emptyMap()
    }
}