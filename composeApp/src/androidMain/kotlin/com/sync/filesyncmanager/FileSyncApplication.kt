package com.sync.filesyncmanager

import android.app.Application
import com.sync.filesyncmanager.data.local.DatabaseFactory
import com.sync.filesyncmanager.util.DataStoreProvider
import com.sync.filesyncmanager.util.FileUtils
import com.sync.filesyncmanager.util.NetworkMonitor

/**
 * Application class that initializes the FileSyncManager's platform-specific components
 */
class FileSyncApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize platform-specific components
        DatabaseFactory.initialize(this)
        DataStoreProvider().initialize(this)
        FileUtils.initialize(this)
        NetworkMonitor().initialize(this)
    }
}