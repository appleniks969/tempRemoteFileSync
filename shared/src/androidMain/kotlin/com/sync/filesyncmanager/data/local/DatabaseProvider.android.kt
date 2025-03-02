package com.sync.filesyncmanager.data.local

import androidx.room.Room
import com.sync.filesyncmanager.AppContextProvider

/**
 * Android implementation of DatabaseProvider
 */
actual object DatabaseProvider {
    private var instance: FileSyncDatabase? = null

    /**
     * Creates or returns the Room database instance for Android
     */
    actual fun getDatabase(): IFileSyncDatabase =
        instance ?: synchronized(this) {
            val db =
                Room
                    .databaseBuilder(
                        AppContextProvider.context,
                        FileSyncDatabase::class.java,
                        FileSyncDatabase.DATABASE_NAME,
                    ).build()

            instance = db
            db
        }
}
