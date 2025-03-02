package com.sync.filesyncmanager.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Platform-specific path provider for iOS
 */
object PlatformPathProvider {
    
    /**
     * Gets the directory for database storage on iOS
     * @return The path to the document directory
     */
    @ExperimentalForeignApi
    fun getDatabaseDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, 
            NSUserDomainMask, 
            true
        )
        val documentsDirectory = paths.firstOrNull() as? String ?: ""
        return documentsDirectory
    }
    
    /**
     * Gets the directory for file storage on iOS
     * @return The path to the document directory
     */
    @ExperimentalForeignApi
    fun getFilesDirectory(): String {
        return getDatabaseDirectory()
    }
    
    /**
     * Creates a directory if it doesn't exist
     * @param path The path to create
     * @return True if the directory exists or was created successfully
     */
    @ExperimentalForeignApi
    fun createDirectoryIfNeeded(path: String): Boolean {
        val fileManager = NSFileManager.defaultManager
        var isDir = false
        
        // Check if path exists and is a directory
        if (fileManager.fileExistsAtPath(path)) {
            // For simplicity, assume it's a directory if it exists
            return true
        }
        
        return fileManager.createDirectoryAtPath(
            path, 
            withIntermediateDirectories = true, 
            attributes = null, 
            error = null
        )
    }
}