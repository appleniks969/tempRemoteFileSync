package com.sync.filesyncmanager

import android.content.Context
import com.sync.filesyncmanager.data.local.DatabaseFactory
import com.sync.filesyncmanager.util.DataStoreProvider
import com.sync.filesyncmanager.util.FileUtils

/**
 * Android-specific initializer for the FileSyncManager
 */
object FileSyncManagerInitializer {
    private lateinit var appContext: Context
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        
        // Initialize other components
        AppContextProvider.initialize(appContext)
        DatabaseFactory.initialize(appContext)
        DataStoreProvider.initialize(appContext)
        FileUtils.initialize(appContext)
    }
    
    fun getContext(): Context {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("FileSyncManagerInitializer not initialized")
        }
        return appContext
    }
}