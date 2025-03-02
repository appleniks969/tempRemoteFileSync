package com.sync.filesyncmanager.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Provider for DataStore access
 */
object DataStoreProvider {
    private lateinit var appContext: Context
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_config")

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getDataStore(): DataStore<Preferences> {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("DataStoreProvider not initialized")
        }
        return appContext.dataStore
    }
}
