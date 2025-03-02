package com.sync.filesyncmanager.util

import com.sync.filesyncmanager.AppContextProvider

/**
 * Android implementation of platform-specific functions for file access
 */
internal actual fun getPlatformCacheDir(): String {
    return AppContextProvider.context.cacheDir.absolutePath
}

internal actual fun getPlatformFilesDir(): String {
    return AppContextProvider.context.filesDir.absolutePath
}