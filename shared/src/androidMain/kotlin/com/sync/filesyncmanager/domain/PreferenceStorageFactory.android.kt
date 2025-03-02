package com.sync.filesyncmanager.domain

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sync.filesyncmanager.util.DataStoreProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual object PreferenceStorageFactory {
    actual fun create(): PreferenceStorage {
        val dataStore = DataStoreProvider.getDataStore()
        return AndroidPreferenceStorage(dataStore)
    }
}

class AndroidPreferenceStorage(
    private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : PreferenceStorage {
    companion object {
        private val CONFIG_KEY = stringPreferencesKey("sync_config")
    }

    override suspend fun getSyncConfig(): SyncConfig {
        val configJson = dataStore.data.map { it[CONFIG_KEY] }.first()
        return parseConfig(configJson)
    }

    override suspend fun updateSyncConfig(config: SyncConfig) {
        val configJson = json.encodeToString(config)
        dataStore.edit { it[CONFIG_KEY] = configJson }
    }

    override fun observeSyncConfig(): Flow<SyncConfig> {
        return dataStore.data.map { it[CONFIG_KEY] }.map { parseConfig(it) }
    }

    private fun parseConfig(configJson: String?): SyncConfig {
        return if (configJson != null) {
            try {
                json.decodeFromString<SyncConfig>(configJson)
            } catch (e: Exception) {
                getDefaultConfig()
            }
        } else {
            getDefaultConfig()
        }
    }

    private fun getDefaultConfig(): SyncConfig {
        return SyncConfig(
            baseUrl = "",
            syncStrategy = SyncStrategy.BIDIRECTIONAL,
            cacheStrategy = CacheStrategy.CACHE_RECENT,
            maxConcurrentTransfers = 3,
            networkType = NetworkType.ANY,
            autoSyncInterval = null,
            syncOnlyOnWifi = false,
            authToken = null,
            compressionEnabled = true,
            unzipFiles = false,
            deleteZipAfterExtract = false,
            retryCount = 3,
            retryDelay = 5000L,
            maxCacheSize = null,
            fileExpiryDuration = null
        )
    }
}