package com.sync.filesyncmanager.domain

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.sync.filesyncmanager.domain.getPlatformFilesDir
import com.sync.filesyncmanager.util.IODispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

actual object PreferenceStorageFactory {
    private var dataStore: DataStore<Preferences>? = null

    actual suspend fun create(): PreferenceStorage {
        val store = dataStore ?: createiOSDataStore()
        return DataStorePreferenceStorage(store)
    }

    private fun createiOSDataStore(): DataStore<Preferences> {
        val docsDir = getPlatformFilesDir()
        val dataStorePath = "$docsDir/file_sync_preferences"

        return PreferenceDataStoreFactory
            .createWithPath(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = CoroutineScope(IODispatcher + SupervisorJob()),
                produceFile = { dataStorePath.toPath() },
            ).also {
                dataStore = it
            }
    }
}
