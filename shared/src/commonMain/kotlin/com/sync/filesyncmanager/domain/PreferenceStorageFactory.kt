package com.sync.filesyncmanager.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sync.filesyncmanager.util.IODispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Factory for creating platform-specific PreferenceStorage implementations
 */
expect object PreferenceStorageFactory {
    suspend fun create(): PreferenceStorage
}

/**
 * Cross-platform DataStore based implementation of PreferenceStorage
 */
class DataStorePreferenceStorage(
    private val dataStore: DataStore<Preferences>,
) : PreferenceStorage {
    // Define preference keys
    private object PreferenceKeys {
        val SYNC_CONFIG = stringPreferencesKey("sync_config")

        // Individual fields (as an alternative to serialized JSON)
        val BASE_URL = stringPreferencesKey("base_url")
        val SYNC_STRATEGY = stringPreferencesKey("sync_strategy")
        val CACHE_STRATEGY = stringPreferencesKey("cache_strategy")
        val MAX_CONCURRENT_TRANSFERS = intPreferencesKey("max_concurrent_transfers")
        val AUTO_SYNC_INTERVAL = longPreferencesKey("auto_sync_interval")
        val NETWORK_TYPE = stringPreferencesKey("network_type")
        val SYNC_ONLY_ON_WIFI = booleanPreferencesKey("sync_only_on_wifi")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val COMPRESSION_ENABLED = booleanPreferencesKey("compression_enabled")
        val UNZIP_FILES = booleanPreferencesKey("unzip_files")
        val DELETE_ZIP_AFTER_EXTRACT = booleanPreferencesKey("delete_zip_after_extract")
        val RETRY_COUNT = intPreferencesKey("retry_count")
        val RETRY_DELAY = longPreferencesKey("retry_delay")
        val MAX_CACHE_SIZE = longPreferencesKey("max_cache_size")
        val FILE_EXPIRY_DURATION = longPreferencesKey("file_expiry_duration")
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getSyncConfig(): SyncConfig {
        // Get the config as a serialized JSON string
        val preferences =
            dataStore.data
                .map { it[PreferenceKeys.SYNC_CONFIG] }
                .flowOn(IODispatcher)
                .map {
                    if (it.isNullOrBlank()) {
                        return@map null
                    } else {
                        try {
                            json.decodeFromString<SyncConfig>(it)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

        // Create a new flow that maps null values to default config
        val config = preferences.map { it ?: getDefaultConfig() }.flowOn(IODispatcher)

        // Return the first value or default if empty
        return try {
            config.flowOn(IODispatcher).first() ?: getDefaultConfig()
        } catch (e: Exception) {
            getDefaultConfig()
        }
    }

    override suspend fun updateSyncConfig(config: SyncConfig) {
        dataStore.edit { preferences ->
            // Store as a serialized JSON string
            val jsonString = json.encodeToString(config)
            preferences[PreferenceKeys.SYNC_CONFIG] = jsonString

            // Also store individual fields for easier access if needed
            preferences[PreferenceKeys.BASE_URL] = config.baseUrl
            preferences[PreferenceKeys.SYNC_STRATEGY] = config.syncStrategy.name
            preferences[PreferenceKeys.CACHE_STRATEGY] = config.cacheStrategy.name
            preferences[PreferenceKeys.MAX_CONCURRENT_TRANSFERS] = config.maxConcurrentTransfers
            preferences[PreferenceKeys.NETWORK_TYPE] = config.networkType.name
            preferences[PreferenceKeys.SYNC_ONLY_ON_WIFI] = config.syncOnlyOnWifi
            preferences[PreferenceKeys.COMPRESSION_ENABLED] = config.compressionEnabled
            preferences[PreferenceKeys.UNZIP_FILES] = config.unzipFiles
            preferences[PreferenceKeys.DELETE_ZIP_AFTER_EXTRACT] = config.deleteZipAfterExtract
            preferences[PreferenceKeys.RETRY_COUNT] = config.retryCount
            preferences[PreferenceKeys.RETRY_DELAY] = config.retryDelay

            // Store nullable fields only if they have values
            config.autoSyncInterval?.let { preferences[PreferenceKeys.AUTO_SYNC_INTERVAL] = it }
            config.authToken?.let { preferences[PreferenceKeys.AUTH_TOKEN] = it }
            config.maxCacheSize?.let { preferences[PreferenceKeys.MAX_CACHE_SIZE] = it }
            config.fileExpiryDuration?.let { preferences[PreferenceKeys.FILE_EXPIRY_DURATION] = it }
        }
    }

    override fun observeSyncConfig(): Flow<SyncConfig> =
        dataStore.data
            .map { preferences ->
                val jsonString = preferences[PreferenceKeys.SYNC_CONFIG]
                if (jsonString.isNullOrBlank()) {
                    getDefaultConfig()
                } else {
                    try {
                        json.decodeFromString<SyncConfig>(jsonString)
                    } catch (e: Exception) {
                        getDefaultConfig()
                    }
                }
            }.flowOn(IODispatcher)

    /**
     * Returns the default configuration
     */
    private fun getDefaultConfig(): SyncConfig =
        SyncConfig(
            baseUrl = "",
            syncStrategy = SyncStrategy.BIDIRECTIONAL,
            cacheStrategy = CacheStrategy.CACHE_RECENT,
            maxConcurrentTransfers = 3,
            autoSyncInterval = null,
            networkType = NetworkType.ANY,
            syncOnlyOnWifi = false,
            authToken = null,
            compressionEnabled = true,
            unzipFiles = false,
            deleteZipAfterExtract = false,
            retryCount = 3,
            retryDelay = 5000L,
            maxCacheSize = null,
            fileExpiryDuration = null,
        )
}
