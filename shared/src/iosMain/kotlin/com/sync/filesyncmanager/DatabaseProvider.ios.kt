package com.sync.filesyncmanager

import androidx.room.InvalidationTracker
import com.sync.filesyncmanager.data.local.FileMetadataDao
import com.sync.filesyncmanager.data.local.FileSyncDatabase

/**
 * iOS implementation of database provider
 * Uses an in-memory implementation since Room isn't available on iOS
 */
object DatabaseProviderIOS {
    
    /**
     * Returns a memory-based implementation of the FileSyncDatabase
     */
    fun getMemoryDatabase(): FileSyncDatabase {
        return InMemoryDatabase()
    }
    
    /**
     * In-memory implementation of FileSyncDatabase for iOS
     */
    private class InMemoryDatabase() : FileSyncDatabase() {
        // Add implementation details here
        // This would typically be a simple in-memory implementation
        // of the database operations defined in FileSyncDatabase
        override fun createInvalidationTracker(): InvalidationTracker {
            TODO("Not yet implemented")
        }

        override fun fileMetadataDao(): FileMetadataDao {
            TODO("Not yet implemented")
        }
    }
}