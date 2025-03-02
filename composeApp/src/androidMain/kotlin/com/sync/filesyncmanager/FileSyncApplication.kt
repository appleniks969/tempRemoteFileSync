package com.sync.filesyncmanager

import android.app.Application
import com.sync.filesyncmanager.data.local.DatabaseProvider
import com.sync.filesyncmanager.util.DataStoreProvider

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

        // Access database to trigger initialization
        DatabaseProvider.getDatabase()
    }
}
