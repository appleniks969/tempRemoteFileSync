package com.sync.filesyncmanager.data.local

import android.content.Context
import androidx.room.Room
import com.sync.filesyncmanager.api.provider.Provider

/**
 * Provider for Room database access
 */
interface AppDatabaseProvider : Provider<FileSyncDatabase>

/**
 * Implementation of DatabaseProvider using Room
 */
class RoomDatabaseProvider(
    private val context: Context,
) : AppDatabaseProvider {
    companion object {
        private const val DATABASE_NAME = "file_sync_database"
        private var instance: FileSyncDatabase? = null
    }

    override fun get(): FileSyncDatabase =
        instance ?: synchronized(this) {
            instance ?: createDatabase().also { instance = it }
        }

    private fun createDatabase(): FileSyncDatabase =
        Room
            .databaseBuilder(
                context.applicationContext,
                FileSyncDatabase::class.java,
                DATABASE_NAME,
            ).build()
}

/**
 * Factory for providing database instances
 */
object DatabaseFactory {
    private var databaseProvider: AppDatabaseProvider? = null

    /**
     * Initializes the database provider
     */
    fun initialize(context: Context) {
        databaseProvider = RoomDatabaseProvider(context)
    }

    /**
     * Gets the database provider
     */
    fun getDatabaseProvider(): AppDatabaseProvider = databaseProvider ?: throw IllegalStateException("Database provider not initialized")

    /**
     * Gets the database instance
     */
    fun getDatabase(): FileSyncDatabase = getDatabaseProvider().get()
}
