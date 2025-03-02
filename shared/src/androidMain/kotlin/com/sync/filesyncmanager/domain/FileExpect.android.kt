package com.sync.filesyncmanager.domain

import com.sync.filesyncmanager.AppContextProvider
import java.io.File

internal actual fun getPlatformCacheDir(): String = AppContextProvider.context.cacheDir.absolutePath

internal actual fun getPlatformFilesDir(): String = AppContextProvider.context.filesDir.absolutePath

internal actual fun getPlatformTempDir(): String {
    val cacheDir = AppContextProvider.context.cacheDir
    val tempDir = File(cacheDir, "temp")
    if (!tempDir.exists()) {
        tempDir.mkdirs()
    }
    return tempDir.absolutePath
}

internal actual fun fileExists(path: String): Boolean = File(path).exists()

internal actual fun isDirectory(path: String): Boolean = File(path).isDirectory

internal actual fun getFileSize(path: String): Long {
    val file = File(path)
    return if (file.exists() && file.isFile) file.length() else -1L
}

internal actual fun getParentPath(path: String): String = File(path).parent ?: ""
