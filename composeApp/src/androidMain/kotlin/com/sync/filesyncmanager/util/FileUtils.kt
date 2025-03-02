package com.sync.filesyncmanager.util

import android.content.Context

/**
 * Utility for file-related operations
 */
object FileUtils {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getCacheDir(): String {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("FileUtils not initialized")
        }
        return appContext.cacheDir.absolutePath
    }

    fun getFilesDir(): String {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("FileUtils not initialized")
        }
        return appContext.filesDir.absolutePath
    }
}
