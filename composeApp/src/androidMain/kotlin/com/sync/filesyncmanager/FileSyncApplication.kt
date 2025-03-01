package com.sync.filesyncmanager

import android.app.Application

/**
 * Application class that initializes the FileSyncManager's platform-specific components
 */
class FileSyncApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize platform-specific components
        FileSyncManagerInitializer.initialize(this)
    }
}