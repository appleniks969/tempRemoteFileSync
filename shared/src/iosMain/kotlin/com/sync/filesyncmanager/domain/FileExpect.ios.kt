package com.sync.filesyncmanager.domain

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL

/**
 * Returns the platform-specific cache directory path for iOS
 */
internal actual fun getPlatformCacheDir(): String {
    val fileManager = NSFileManager.defaultManager
    val urls = fileManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
    return (urls.firstOrNull() as? NSURL)?.path ?: ""
}

/**
 * Returns the platform-specific files/documents directory path for iOS
 */
internal actual fun getPlatformFilesDir(): String {
    val fileManager = NSFileManager.defaultManager
    val urls = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    return (urls.firstOrNull() as? NSURL)?.path ?: ""
}