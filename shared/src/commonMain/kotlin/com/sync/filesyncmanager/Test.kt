

/* Factory for creating FileSyncManager */
// src/commonMain/kotlin/com/sync/filesyncmanager/api/FileSyncManagerFactory.kt

package com.sync.filesyncmanager.api

import com.sync.filesyncmanager.data.RemoteFileRepositoryImpl
import com.sync.filesyncmanager.domain.SyncConfig

/**
 * Factory for creating FileSyncManager instances
 */
expect class FileSyncManagerFactory() {
    /**
     * Creates a FileSyncManager instance
     */
    suspend fun create(
        initialConfig: SyncConfig? = null
    ): FileSyncManager

    /**
     * Creates an HTTP client
     */
    fun createHttpClient(): HttpClient

    /**
     * Gets the filesystem for this platform
     */
    fun getFileSystem(): FileSystem
}

/* Factory implementation for Android */
// src/androidMain/kotlin/com/sync/filesyncmanager/api/FileSyncManagerFactoryAndroid.kt

package com.sync.filesyncmanager.api

import android.content.Context
import com.sync.filesyncmanager.data.local.DatabaseProvider

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
        // Create DataStore
        val dataStore = DataStoreWrapper(context)

        // Create file system and services
        val fileSystem = getFileSystem()
        val fileService = FileService(fileSystem)
        val zipService = ZipService(fileSystem, fileService)

        // Create repositories
        val database = DatabaseProvider.getDatabase(context)
        val metadataRepo = FileMetadataRepositoryImpl(database)
        val localRepo = LocalFileRepositoryImpl(fileService)
        val configRepo = ConfigRepositoryImpl(dataStore, json)

        // Create network monitor
        val networkMonitor = NetworkMonitor()

        // Create scheduler
        val syncScheduler = SyncScheduler()

        // Create HTTP client
        val httpClient = createHttpClient()

        // Get initial config
        val config = initialConfig ?: configRepo.getSyncConfig()

        // Create remote repository
        val remoteRepo = RemoteFileRepositoryImpl(
            httpClient = httpClient,
            baseUrl = config.baseUrl,
            authToken = config.authToken
        )

        // Create and return the FileSyncManager
        return FileSyncManagerImpl(
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
            install(Logging) {
                level = LogLevel.INFO
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

/* Factory implementation for iOS */
// src/iosMain/kotlin/com/sync/filesyncmanager/api/FileSyncManagerFactoryIOS.kt

package com.sync.filesyncmanager.api

import platform.Foundation.NSUserDefaults

actual class FileSyncManagerFactory {
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
        // Create DataStore
        val dataStore = DataStoreWrapper()

        // Create file system and services
        val fileSystem = getFileSystem()
        val fileService = FileService(fileSystem)
        val zipService = ZipService(fileSystem, fileService)

        // Create repositories
        val database = createDatabase()
        val metadataRepo = FileMetadataRepositoryImpl(database)
        val localRepo = LocalFileRepositoryImpl(fileService)
        val configRepo = ConfigRepositoryImpl(dataStore, json)

        // Create network monitor
        val networkMonitor = NetworkMonitor()

        // Create scheduler and register background tasks
        val syncScheduler = SyncScheduler()
        syncScheduler.registerTasks()

        // Create HTTP client
        val httpClient = createHttpClient()

        // Get initial config
        val config = initialConfig ?: configRepo.getSyncConfig()

        // Create remote repository
        val remoteRepo = RemoteFileRepositoryImpl(
            httpClient = httpClient,
            baseUrl = config.baseUrl,
            authToken = config.authToken
        )

        // Create and return the FileSyncManager
        return FileSyncManagerImpl(
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
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }

    /**
     * Gets the filesystem for this platform
     */
    actual fun getFileSystem(): FileSystem {
        return FileSystem.SYSTEM
    }

    /**
     * Creates the Room database for iOS
     */
    private fun createDatabase(): com.sync.filesyncmanager.data.local.FileSyncDatabase {
        // This would be the iOS-specific implementation to create Room database
        // For iOS, Room would handle this internally based on the platform
        return androidx.room.Room.databaseBuilder(
            /* No context needed for iOS */
            org.koin.core.component.KoinComponent::class.java, // Placeholder
            com.sync.filesyncmanager.data.local.FileSyncDatabase::class.java,
            "file_sync_database.db"
        ).build()
    }
}

/* Android Main Application Usage Example */
// src/androidMain/kotlin/com/sync/filesyncmanager/FileSyncManagerInitializer.kt

package com.sync.filesyncmanager

import android.content.Context
import com.sync.filesyncmanager.api.FileSyncManager

object FileSyncManagerInitializer {
    /**
     * Creates a FileSyncManager instance
     */
    suspend fun createFileSyncManager(
        context: Context,
        initialConfig: SyncConfig? = null
    ): FileSyncManager {
        // Create factory
        val factory = FileSyncManagerFactory(context)

        // Create and return manager
        return factory.create(initialConfig)
    }
}

/**
 * Example usage
 */
class FileSyncManagerExample(private val context: Context) {
    suspend fun example() {
        // Create manager with ZIP support
        val config = SyncConfig(
            baseUrl = "https://api.example.com",
            unzipFiles = true,
            deleteZipAfterExtract = true
        )

        val syncManager = FileSyncManagerInitializer.createFileSyncManager(context, config)

        // Use the manager
        syncManager.syncFile("example-file-id").collect { progress ->
            // Handle progress updates
            println("Progress: ${progress.progress * 100}%")

            if (progress.status == com.sync.filesyncmanager.domain.SyncStatus.SYNCED) {
                // Get the path to the file or extracted content
                val filePath = progress.filePath
                println("Sync complete: $filePath")
            }
        }
    }
}
