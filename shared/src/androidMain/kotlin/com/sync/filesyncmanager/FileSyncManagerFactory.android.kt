package com.sync.filesyncmanager

import com.sync.filesyncmanager.api.FileSyncManager
import com.sync.filesyncmanager.api.FileSyncManagerImpl
import com.sync.filesyncmanager.data.local.DatabaseProvider
import com.sync.filesyncmanager.data.local.IFileSyncDatabase
import com.sync.filesyncmanager.domain.ConfigRepository
import com.sync.filesyncmanager.domain.FileMetadataRepository
import com.sync.filesyncmanager.domain.LocalFileRepository
import com.sync.filesyncmanager.domain.PathUtils
import com.sync.filesyncmanager.domain.PreferenceStorageFactory
import com.sync.filesyncmanager.domain.RemoteFileRepository
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.getPlatformFilesDir
import com.sync.filesyncmanager.util.FileService
import com.sync.filesyncmanager.util.NetworkMonitor
import com.sync.filesyncmanager.util.SyncScheduler
import com.sync.filesyncmanager.util.ZipService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okio.FileSystem

actual class FileSyncManagerFactory {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        }

    /**
     * Ensures required directories exist
     */
    actual suspend fun ensureRequiredDirectories() {
        val fileSystem = getFileSystem()
        val fileService = FileService(fileSystem)

        // Create downloads directory
        val downloadsDir = PathUtils.combine(getPlatformFilesDir(), "downloads")
        fileService.createDirectory(downloadsDir)
    }

    actual suspend fun create(initialConfig: SyncConfig?): FileSyncManager {
        // Create file system and services
        val fileSystem = getFileSystem()
        val fileService = FileService(fileSystem)
        val zipService = ZipService()

        // Ensure directories exist
        ensureRequiredDirectories()

        // Create repositories
        val database = getDatabase()
        val metadataRepo = FileMetadataRepository(database)
        val localRepo = LocalFileRepository(fileService)

        // Get preference storage
        val preferenceStorage = PreferenceStorageFactory.create()
        val configRepo = ConfigRepository(preferenceStorage)

        // Create network monitor and scheduler
        val networkMonitor = NetworkMonitor()
        val syncScheduler = SyncScheduler()

        // Create HTTP client
        val httpClient = createHttpClient()

        // Get initial config
        val config = initialConfig ?: configRepo.getSyncConfig()

        // Create remote repository
        val remoteRepo =
            RemoteFileRepository(
                fileService = fileService,
                httpClient = httpClient,
                baseUrl = config.baseUrl,
                authToken = config.authToken,
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
            dispatcher = Dispatchers.Default,
        )
    }

    actual fun createHttpClient(): HttpClient =
        HttpClient {
            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 60000 // 60 seconds for requests
                connectTimeoutMillis = 15000 // 15 seconds for connection
                socketTimeoutMillis = 60000 // 60 seconds for socket read/write
            }
        }

    actual fun getFileSystem(): FileSystem = FileSystem.SYSTEM

    actual fun getDatabase(): IFileSyncDatabase = DatabaseProvider.getDatabase()
}
