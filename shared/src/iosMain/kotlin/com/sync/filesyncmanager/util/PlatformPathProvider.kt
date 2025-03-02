package com.sync.filesyncmanager.util

import com.sync.filesyncmanager.domain.PathUtils
import com.sync.filesyncmanager.domain.getPlatformCacheDir
import com.sync.filesyncmanager.domain.getPlatformFilesDir
import com.sync.filesyncmanager.domain.getPlatformTempDir
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

/**
 * Platform-specific path provider for iOS
 */
object PlatformPathProvider {
    /**
     * Gets the directory for database storage on iOS
     * @return The path to the document directory
     */
    fun getDatabaseDirectory(): String = getPlatformFilesDir()

    /**
     * Gets the directory for file storage on iOS
     * @return The path to the document directory
     */
    fun getFilesDirectory(): String = getPlatformFilesDir()

    /**
     * Gets the directory for temporary files on iOS
     * @return The path to the temp directory
     */
    fun getTempDirectory(): String = getPlatformTempDir()

    /**
     * Gets the directory for cached files on iOS
     * @return The path to the cache directory
     */
    fun getCacheDirectory(): String = getPlatformCacheDir()

    /**
     * Creates a directory if it doesn't exist
     * @param path The path to create
     * @return True if the directory exists or was created successfully
     */
    @ExperimentalForeignApi
    fun createDirectoryIfNeeded(path: String): Boolean {
        // If directory already exists
        if (PathUtils.exists(path) && PathUtils.isDirectory(path)) {
            return true
        }

        val fileManager = NSFileManager.defaultManager
        return fileManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    /**
     * Combines path segments in a platform-specific way
     */
    fun combinePath(vararg segments: String): String = PathUtils.combine(*segments)
}
