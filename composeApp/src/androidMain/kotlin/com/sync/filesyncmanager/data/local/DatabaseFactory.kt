package com.sync.filesyncmanager.data.local

import android.content.Context
import androidx.room.Room
import com.sync.filesyncmanager.data.local.FileSyncDatabase

/**
 * Factory for creating and accessing the Room database
 */
object DatabaseFactory {
    private var database: FileSyncDatabase? = null

    fun initialize(context: Context) {
        database = Room.databaseBuilder(
            context.applicationContext,
            FileSyncDatabase::class.java,
            "file_sync_database"
        ).build()
    }

    fun getDatabase(): FileSyncDatabase {
        return database ?: throw IllegalStateException("Database not initialized")
    }
}