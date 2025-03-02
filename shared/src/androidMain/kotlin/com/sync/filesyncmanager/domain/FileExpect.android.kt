package com.sync.filesyncmanager.domain

import com.sync.filesyncmanager.AppContextProvider

internal actual fun getPlatformCacheDir(): String {
    return AppContextProvider.context.cacheDir.absolutePath
}

internal actual fun getPlatformFilesDir(): String {
    return AppContextProvider.context.filesDir.absolutePath
}