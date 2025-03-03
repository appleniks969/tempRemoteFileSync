package com.sync.filesyncmanager.api.cache

import com.sync.filesyncmanager.domain.CacheStrategy
import com.sync.filesyncmanager.domain.FileMetadata
import com.sync.filesyncmanager.domain.FileMetadataRepository
import com.sync.filesyncmanager.domain.LocalFileRepository
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.SyncStatus
import kotlinx.datetime.Clock

/**
 * Manages the caching of files based on configured strategies
 */
interface CacheManager {
    /**
     * Clears cache according to the provided configuration
     * @return Number of bytes cleared
     */
    suspend fun clearCache(config: SyncConfig): Long
}

/**
 * Implementation of CacheManager
 */
class CacheManagerImpl(
    private val metadataRepo: FileMetadataRepository,
    private val localRepo: LocalFileRepository,
) : CacheManager {
    override suspend fun clearCache(config: SyncConfig): Long {
        val currentSize = localRepo.getTotalCacheSize()

        return when (config.cacheStrategy) {
            CacheStrategy.NO_CACHE -> {
                clearAllCache()
                currentSize
            }
            CacheStrategy.CACHE_RECENT -> {
                clearExpiredCache(config)
            }
            CacheStrategy.CACHE_PRIORITY -> {
                clearLowPriorityCache(config, currentSize)
            }
            else -> 0L
        }
    }

    private suspend fun clearAllCache(): Long {
        // Delete all local files
        metadataRepo.getAllFileMetadata().forEach { metadata ->
            if (metadata.isDownloaded) {
                clearFile(metadata)
            }
        }
        return localRepo.getTotalCacheSize()
    }

    private suspend fun clearExpiredCache(config: SyncConfig): Long {
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
            if (clearFile(metadata)) {
                bytesCleared += metadata.fileSize
            }
        }
        return bytesCleared
    }

    private suspend fun clearLowPriorityCache(
        config: SyncConfig,
        currentSize: Long,
    ): Long {
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

                if (clearFile(metadata)) {
                    bytesCleared += metadata.fileSize
                }
            }
            return bytesCleared
        }
        return 0L
    }

    private suspend fun clearFile(metadata: FileMetadata): Boolean {
        val deleted = localRepo.deleteFile(metadata.filePath)

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

        return deleted
    }
}
