package com.sync.filesyncmanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sync.filesyncmanager.domain.FileMetadata

@Database(
    entities = [FileMetadata::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(TypeConverters::class)
abstract class FileSyncDatabase : RoomDatabase() {
    abstract fun fileMetadataDao(): FileMetadataDao
}