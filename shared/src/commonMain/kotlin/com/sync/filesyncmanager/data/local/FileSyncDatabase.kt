package com.sync.filesyncmanager.data.local

import androidx.room.*
import com.sync.filesyncmanager.domain.FileMetadata
import com.sync.filesyncmanager.domain.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Room database definition for KMP
 */
@Database(
    entities = [FileMetadata::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(FileSyncTypeConverters::class)
abstract class FileSyncDatabase : RoomDatabase() {
    abstract fun fileMetadataDao(): FileMetadataDao

    companion object {
        // Database name for both platforms
        const val DATABASE_NAME = "file_sync_database"
    }
}

/**
 * Data Access Object for FileMetadata
 */
@Dao
interface FileMetadataDao {
    @Query("SELECT * FROM file_metadata WHERE fileId = :fileId")
    suspend fun getById(fileId: String): FileMetadata?

    @Query("SELECT * FROM file_metadata WHERE filePath = :path")
    suspend fun getByPath(path: String): FileMetadata?

    @Query("SELECT * FROM file_metadata WHERE isDeleted = 0")
    suspend fun getAll(): List<FileMetadata>

    @Query("SELECT * FROM file_metadata WHERE syncStatus != 'SYNCED' AND isDeleted = 0")
    suspend fun getUnsyncedFiles(): List<FileMetadata>

    @Query("SELECT * FROM file_metadata WHERE isDownloaded = 0 AND isDeleted = 0")
    suspend fun getPendingDownloads(): List<FileMetadata>

    @Query("SELECT * FROM file_metadata WHERE isUploaded = 0 AND isDeleted = 0")
    suspend fun getPendingUploads(): List<FileMetadata>

    @Query("SELECT * FROM file_metadata WHERE fileId = :fileId")
    fun observeById(fileId: String): Flow<FileMetadata?>

    @Query("SELECT * FROM file_metadata WHERE isDeleted = 0")
    fun observeAll(): Flow<List<FileMetadata>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: FileMetadata)

    @Query("UPDATE file_metadata SET syncStatus = :status, lastSyncTime = :lastSyncTime WHERE fileId = :fileId")
    suspend fun updateSyncStatus(fileId: String, status: SyncStatus, lastSyncTime: Instant)

    @Query("UPDATE file_metadata SET isDownloaded = :isDownloaded, localChecksum = :checksum, syncStatus = :status, lastSyncTime = :lastSyncTime WHERE fileId = :fileId")
    suspend fun updateDownloadStatus(fileId: String, isDownloaded: Boolean, checksum: String?, status: SyncStatus, lastSyncTime: Instant)

    @Query("UPDATE file_metadata SET isUploaded = :isUploaded, remoteChecksum = :checksum, syncStatus = :status, lastSyncTime = :lastSyncTime WHERE fileId = :fileId")
    suspend fun updateUploadStatus(fileId: String, isUploaded: Boolean, checksum: String?, status: SyncStatus, lastSyncTime: Instant)

    @Query("DELETE FROM file_metadata WHERE fileId = :fileId")
    suspend fun delete(fileId: String)

    @Query("UPDATE file_metadata SET isDeleted = 1 WHERE fileId = :fileId")
    suspend fun markAsDeleted(fileId: String)

    @Query("DELETE FROM file_metadata")
    suspend fun deleteAll()
}

/**
 * Type converters for Room database
 */
class FileSyncTypeConverters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilliseconds()
    }

    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String {
        return status.name
    }

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return SyncStatus.valueOf(value)
    }

    @TypeConverter
    fun fromBoolean(value: Boolean): Int {
        return if (value) 1 else 0
    }

    @TypeConverter
    fun toBoolean(value: Int): Boolean {
        return value != 0
    }
}

/**
 * Cross-platform database provider
 */
expect object DatabaseProvider {
    /**
     * Creates or returns the database instance
     */
    fun getDatabase(): FileSyncDatabase
}