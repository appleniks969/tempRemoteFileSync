package com.sync.filesyncmanager.domain

/**
 * Gets the platform-specific cache directory path
 * @return the platform-specific cache directory path as a string
 */
internal expect fun getPlatformCacheDir(): String

/**
 * Gets the platform-specific files directory path
 * @return the platform-specific files directory path as a string
 */
internal expect fun getPlatformFilesDir(): String

/**
 * Gets the platform-specific temporary directory path
 * @return the platform-specific temporary directory path as a string
 */
internal expect fun getPlatformTempDir(): String

/**
 * Checks if a file exists at the given path
 * @param path The path to check
 * @return true if the file exists
 */
internal expect fun fileExists(path: String): Boolean

/**
 * Checks if a path is a directory
 * @param path The path to check
 * @return true if the path is a directory
 */
internal expect fun isDirectory(path: String): Boolean

/**
 * Gets the size of a file
 * @param path The path to the file
 * @return The size of the file in bytes, or -1 if the file doesn't exist
 */
internal expect fun getFileSize(path: String): Long

/**
 * Gets the parent directory of a path
 * @param path The path to get the parent of
 * @return The parent directory path
 */
internal expect fun getParentPath(path: String): String

/**
 * Cross-platform path utilities
 */
object PathUtils {
    /**
     * Combines path segments in a platform-specific way
     */
    fun combine(vararg segments: String): String {
        var result = segments.firstOrNull() ?: ""
        for (i in 1 until segments.size) {
            val segment = segments[i].trim('/').trim('\\')
            result = if (result.isEmpty()) segment else "$result/$segment"
        }
        return result
    }

    /**
     * Gets the file name from a path
     */
    fun getFileName(path: String): String {
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash >= 0 && lastSlash < path.length - 1) {
            path.substring(lastSlash + 1)
        } else {
            path
        }
    }

    /**
     * Gets the file extension from a path
     */
    fun getFileExtension(path: String): String {
        val fileName = getFileName(path)
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0) {
            fileName.substring(lastDot + 1)
        } else {
            ""
        }
    }

    /**
     * Gets the parent directory of a path
     */
    fun getParentDirectory(path: String): String = getParentPath(path)

    /**
     * Checks if the path exists
     */
    fun exists(path: String): Boolean = fileExists(path)

    /**
     * Checks if the path is a directory
     */
    fun isDirectory(path: String): Boolean = isDirectory(path)

    /**
     * Gets the file size
     */
    fun getFileSize(path: String): Long = getFileSize(path)
}
