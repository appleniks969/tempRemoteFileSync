package com.sync.filesyncmanager.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Implementation of ConfigRepository using platform-specific storage
 */
class ConfigRepository(private val configStore: Any, private val json: Json) {

    companion object {
        // Keys for storage
        private const val SYNC_CONFIG_KEY = "sync_config"

        // Default values
        private const val DEFAULT_BASE_URL = ""
        private const val DEFAULT_MAX_CONCURRENT_TRANSFERS = 3
        private const val DEFAULT_RETRY_COUNT = 3
        private const val DEFAULT_RETRY_DELAY = 5000L
    }

    /**
     * Gets the current sync configuration
     */
    suspend fun getSyncConfig(): SyncConfig {
        return when (configStore) {
            is androidx.datastore.core.DataStore<*> -> {
                @Suppress("UNCHECKED_CAST")
                val dataStore = configStore as androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
                val prefKey = androidx.datastore.preferences.core.stringPreferencesKey(SYNC_CONFIG_KEY)
                val configJson = dataStore.data.map { it[prefKey] }.first()
                
                parseConfig(configJson)
            }
            is com.sync.filesyncmanager.IosConfigStore -> {
                val iosStore = configStore as com.sync.filesyncmanager.IosConfigStore
                val configJson = iosStore.getConfigData(SYNC_CONFIG_KEY)
                
                parseConfig(configJson)
            }
            else -> getDefaultConfig()
        }
    }

    /**
     * Updates the sync configuration
     */
    suspend fun updateSyncConfig(config: SyncConfig) {
        val configJson = json.encodeToString(config)
        
        when (configStore) {
            is androidx.datastore.core.DataStore<*> -> {
                @Suppress("UNCHECKED_CAST")
                val dataStore = configStore as androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
                val prefKey = androidx.datastore.preferences.core.stringPreferencesKey(SYNC_CONFIG_KEY)
                dataStore.edit { it[prefKey] = configJson }
            }
            is com.sync.filesyncmanager.IosConfigStore -> {
                val iosStore = configStore as com.sync.filesyncmanager.IosConfigStore
                iosStore.setConfigData(SYNC_CONFIG_KEY, configJson)
            }
        }
    }

    /**
     * Observes changes to the sync configuration
     */
    fun observeSyncConfig(): Flow<SyncConfig> {
        return when (configStore) {
            is androidx.datastore.core.DataStore<*> -> {
                @Suppress("UNCHECKED_CAST")
                val dataStore = configStore as androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
                val prefKey = androidx.datastore.preferences.core.stringPreferencesKey(SYNC_CONFIG_KEY)
                dataStore.data.map { it[prefKey] }.map { parseConfig(it) }
            }
            else -> {
                // For iOS, we don't have built-in reactivity, so we'll just emit the current value
                flow { emit(getSyncConfig()) }
            }
        }
    }

    /**
     * Parses the config JSON string
     */
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

    /**
     * Resets the configuration to default values
     */
    suspend fun resetToDefaults() {
        updateSyncConfig(getDefaultConfig())
    }

    /**
     * Returns the default configuration
     */
    private fun getDefaultConfig(): SyncConfig {
        return SyncConfig(
            baseUrl = DEFAULT_BASE_URL,
            syncStrategy = SyncStrategy.BIDIRECTIONAL,
            cacheStrategy = CacheStrategy.CACHE_RECENT,
            maxConcurrentTransfers = DEFAULT_MAX_CONCURRENT_TRANSFERS,
            autoSyncInterval = null,
            networkType = NetworkType.ANY,
            syncOnlyOnWifi = false,
            authToken = null,
            compressionEnabled = true,
            unzipFiles = false,
            deleteZipAfterExtract = false,
            retryCount = DEFAULT_RETRY_COUNT,
            retryDelay = DEFAULT_RETRY_DELAY,
            maxCacheSize = null,
            fileExpiryDuration = null
        )
    }
}