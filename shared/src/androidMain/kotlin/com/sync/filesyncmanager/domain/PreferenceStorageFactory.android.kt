package com.sync.filesyncmanager.domain

import com.sync.filesyncmanager.util.DataStoreProvider

actual object PreferenceStorageFactory {
    actual suspend fun create(): PreferenceStorage {
        val dataStore = DataStoreProvider.getDataStore()
        return DataStorePreferenceStorage(dataStore)
    }
}
