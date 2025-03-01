package com.sync.filesyncmanager

import com.sync.filesyncmanager.api.FileSyncManager
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

actual class FileSyncManagerFactory {
    private val configStore = IosConfigStore()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }
    
    /**
     * Creates a FileSyncManager instance
     */
    actual suspend fun create(initialConfig: SyncConfig?): FileSyncManager {
        // Create file system and services
        val fileSystem = getFileSystem()
        val fileService = FileService(fileSystem)
        val zipService = ZipService(fileSystem, fileService)

        // Create repositories
        val memoryDb = DatabaseProviderIOS.getMemoryDatabase()
        val metadataRepo = FileMetadataRepository(memoryDb)
        val localRepo = LocalFileRepository(fileService)
        val configRepo = ConfigRepository(configStore, json)

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