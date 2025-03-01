package com.sync.filesyncmanager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.sync.filesyncmanager.api.FileSyncManager
import com.sync.filesyncmanager.data.local.FileSyncDatabase
import com.sync.filesyncmanager.domain.ConfigRepository
import com.sync.filesyncmanager.domain.FileMetadataRepository
import com.sync.filesyncmanager.domain.LocalFileRepository
import com.sync.filesyncmanager.domain.RemoteFileRepository
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.util.FileService
import com.sync.filesyncmanager.util.NetworkMonitor
import com.sync.filesyncmanager.util.SyncScheduler
import com.sync.filesyncmanager.util.ZipService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okio.FileSystem

// Define a DataStore for the context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_config")

/**
 * DatabaseProvider for Android platform
 */
object DatabaseProvider {
    private var database: FileSyncDatabase? = null

    fun getDatabase(context: Context): FileSyncDatabase {
        return database ?: synchronized(this) {
            val db = androidx.room.Room.databaseBuilder(
                context.applicationContext,
                FileSyncDatabase::class.java,
                "file_sync_database"
            ).build()
            database = db
            db
        }
    }
}

actual class FileSyncManagerFactory(
    private val context: Context
) {
    init {
        // Initialize the context provider
        AppContextProvider.initialize(context)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    /**
     * Creates a FileSyncManager instance
     */
    actual suspend fun create(
        initialConfig: SyncConfig?
    ): FileSyncManager {
        // Create file system and services
        val fileSystem = getFileSystem()
        val fileService = FileService(fileSystem)
        val zipService = ZipService(fileSystem, fileService)

        // Create repositories
        val database = DatabaseProvider.getDatabase(context)
        val metadataRepo = FileMetadataRepository(database)
        val localRepo = LocalFileRepository(fileService)
        val configRepo = ConfigRepository(context.dataStore, json)

        // Create network monitor
        val networkMonitor = NetworkMonitor()

        // Create scheduler
        val syncScheduler = SyncScheduler(context)

        // Create HTTP client
        val httpClient = createHttpClient()

        // Get initial config
        val config = initialConfig ?: configRepo.getSyncConfig()

        // Create remote repository
        val remoteRepo = RemoteFileRepository(
            fileService = fileService,
            httpClient = httpClient,
            baseUrl = config.baseUrl,
            authToken = config.authToken
        )

        // Create and return the FileSyncManager
        return FileSyncManager(
            metadataRepo = metadataRepo,
            remoteRepo = remoteRepo,
            localRepo = localRepo,
            configRepo = configRepo,
            networkMonitor = networkMonitor,
            syncScheduler = syncScheduler,
            zipService = zipService,
            dispatcher = Dispatchers.Default
        )
    }

    /**
     * Creates an HTTP client
     */
    actual fun createHttpClient(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    /**
     * Gets the filesystem for this platform
     */
    actual fun getFileSystem(): FileSystem {
        return FileSystem.SYSTEM
    }
}
