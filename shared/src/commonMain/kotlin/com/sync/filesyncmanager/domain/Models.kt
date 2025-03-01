package com.sync.filesyncmanager.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Enumeration representing the synchronization status of a file
 */
enum class SyncStatus {
    PENDING,     // File is waiting to be processed
    DOWNLOADING, // File is currently being downloaded
    UPLOADING,   // File is currently being uploaded
    SYNCED,      // File is fully synchronized
    FAILED,      // Synchronization failed
    CONFLICT     // There is a conflict between local and remote versions
}

/**
 * Enumeration representing the synchronization strategy
 */
enum class SyncStrategy {
    DOWNLOAD_ONLY,       // Only download files from remote to local
    UPLOAD_ONLY,         // Only upload files from local to remote
    BIDIRECTIONAL,       // Sync in both directions
    LOCAL_WINS,          // In case of conflict, local version wins
    REMOTE_WINS,         // In case of conflict, remote version wins
    NEWEST_WINS          // In case of conflict, newest version wins
}

/**
 * Enumeration representing caching strategies
 */
enum class CacheStrategy {
    NO_CACHE,           // Don't cache files locally
    CACHE_RECENT,       // Cache only recently accessed files
    CACHE_ALL,          // Cache all files locally
    CACHE_PRIORITY      // Cache files based on priority
}

/**
 * Enumeration representing network type for sync constraints
 */
enum class NetworkType {
    ANY,           // Any network connection
    WIFI_ONLY,     // Only WiFi connections
    UNMETERED_ONLY, // Only unmetered connections
    NONE           // Offline mode
}

/**
 * Structured info on file synchronization progress
 */
@Serializable
data class SyncProgress(
    val fileId: String,
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val progress: Float, // 0.0f to 1.0f
    val status: SyncStatus,
    val isDownload: Boolean, // true if downloading, false if uploading
    val filePath: String? = null // Path to the file or extracted directory
)

/**
 * Represents file metadata for both local and remote files
 */
@Entity(tableName = "file_metadata")
@Serializable
data class FileMetadata(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val filePath: String,
    val remoteUrl: String,
    val lastModified: Instant,
    val fileSize: Long,
    val syncStatus: SyncStatus,
    val localChecksum: String? = null,
    val remoteChecksum: String? = null,
    val lastSyncTime: Instant? = null,
    val isDownloaded: Boolean = false,
    val isUploaded: Boolean = false,
    val isDeleted: Boolean = false,
    val priority: Int = 0,
    val expiryTime: Instant? = null,
    val isZipFile: Boolean = false,
    val extractedPath: String? = null, // Path to the directory where the ZIP was extracted
    val isExtracted: Boolean = false
)

/**
 * Configuration for the file sync manager
 */
@Serializable
data class SyncConfig(
    val baseUrl: String,
    val syncStrategy: SyncStrategy = SyncStrategy.BIDIRECTIONAL,
    val cacheStrategy: CacheStrategy = CacheStrategy.CACHE_RECENT,
    val maxConcurrentTransfers: Int = 3,
    val autoSyncInterval: Long? = null, // In milliseconds, null means no auto sync
    val networkType: NetworkType = NetworkType.ANY,
    val syncOnlyOnWifi: Boolean = false,
    val authToken: String? = null,
    val compressionEnabled: Boolean = true,
    val unzipFiles: Boolean = false, // Whether to unzip files after download
    val deleteZipAfterExtract: Boolean = false, // Whether to delete the original ZIP after extraction
    val retryCount: Int = 3,
    val retryDelay: Long = 5000, // In milliseconds
    val maxCacheSize: Long? = null, // In bytes, null means no limit
    val fileExpiryDuration: Long? = null // In milliseconds, null means no expiry
)

/**
 * Represents the result of a synchronization operation
 */
sealed class SyncResult {
    @Serializable
    data class Success(val fileMetadata: FileMetadata) : SyncResult()

    @Serializable
    data class Error(val fileId: String?, val errorMessage: String, val exception: String? = null) : SyncResult()

    @Serializable
    data class Conflict(val localMetadata: FileMetadata, val remoteMetadata: FileMetadata) : SyncResult()
}

/**
 * Represents batch sync results
 */
@Serializable
data class BatchSyncResult(
    val successCount: Int,
    val failedCount: Int,
    val conflictCount: Int,
    val failedFiles: List<Pair<String, String>>, // Pairs of fileId to error message
    val totalProcessed: Int
)
