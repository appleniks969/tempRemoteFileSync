package com.sync.filesyncmanager.domain

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun getPlatformCacheDir(): String {
    val paths =
        NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true,
        )
    return (paths.firstOrNull() as? String) ?: ""
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getPlatformFilesDir(): String {
    val paths =
        NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        )
    return (paths.firstOrNull() as? String) ?: ""
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getPlatformTempDir(): String {
    val tempDir = NSTemporaryDirectory()
    val manager = NSFileManager.defaultManager
    val tempSubDir = "$tempDir/com.sync.filesyncmanager/temp"

    // Create the directory if it doesn't exist
    if (!manager.fileExistsAtPath(tempSubDir)) {
        manager.createDirectoryAtPath(
            tempSubDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    return tempSubDir
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun fileExists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

@OptIn(ExperimentalForeignApi::class)
internal actual fun isDirectory(path: String): Boolean {
    // For iOS, we need a simpler approach
    return NSFileManager.defaultManager.fileExistsAtPath(path) &&
        !NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, error = null).isNullOrEmpty()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getFileSize(path: String): Long {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
    return attributes?.get("NSFileSize") as? Long ?: -1L
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getParentPath(path: String): String {
    val lastSlash = path.lastIndexOf('/')
    return if (lastSlash > 0) {
        path.substring(0, lastSlash)
    } else {
        ""
    }
}
