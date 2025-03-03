package com.sync.filesyncmanager

import android.app.Application
import com.sync.filesyncmanager.data.local.DatabaseFactory
import com.sync.filesyncmanager.util.DataStoreProvider
import com.sync.filesyncmanager.util.FileUtils

/**
 * Application class that initializes the FileSyncManager's platform-specific components
 */
class FileSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize platform-specific components
        initializeComponents()
    }

    private fun initializeComponents() {
        // Initialize AppContextProvider
        AppContextProvider.initialize(this)

        // Initialize DataStore
        DataStoreProvider.initialize(this)

        // Initialize FileUtils
        FileUtils.initialize(this)

        // Initialize and access database
        try {
            DatabaseFactory.initialize(this)
            DatabaseFactory.getDatabase()
        } catch (e: Exception) {
            // Log error but don't crash the app
            e.printStackTrace()
        }
    }
}
