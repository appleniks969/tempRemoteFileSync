package com.sync.filesyncmanager.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Provider for DataStore access
 */
object DataStoreProvider {
    private lateinit var appContext: Context
    private var dataStore: DataStore<Preferences>? = null
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (dataStore == null) {
            dataStore = createDataStore()
        }
    }

    fun getDataStore(): DataStore<Preferences> {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("DataStoreProvider not initialized")
        }
        return dataStore ?: createDataStore().also { dataStore = it }
    }
    
    private fun createDataStore(): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStoreFile = File(appContext.filesDir, "sync_config.preferences_pb")
        return PreferenceDataStoreFactory.create(
            corruptionHandler = null,
            migrations = emptyList(),
            scope = scope,
            produceFile = { dataStoreFile }
        )
    }
}
