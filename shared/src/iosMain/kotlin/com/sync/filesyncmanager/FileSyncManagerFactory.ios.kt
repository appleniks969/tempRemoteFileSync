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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okio.FileSystem
import platform.Foundation.NSFileManager
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask

/**
 * In-memory database for iOS
 */
object DatabaseProviderIOS {
    private val memoryFileMetadataMap = mutableMapOf<String, Any>()
    
    fun getMemoryDatabase(): Any {
        return memoryFileMetadataMap
    }
}

/**
 * Stores iOS app configuration
 */
class IosConfigStore {
    // Using UserDefaults would be better but keeping simple for this implementation
    private val configData = mutableMapOf<String, String>()
    
    fun getConfigData(key: String): String? {
        return configData[key]
    }
    
    fun setConfigData(key: String, value: String) {
        configData[key] = value
    }
}

actual class FileSyncManagerFactory actual constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

        // Create scheduler
        val syncScheduler = SyncScheduler()

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
    
    /**
     * Gets the app's documents directory
     */
    fun getDocumentsDirectory(): String {
        val fileManager = NSFileManager.defaultManager
        val urls = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        return urls.firstOrNull()?.path ?: ""
    }
}