package com.sync.filesyncmanager.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager

/**
 * iOS implementation of ZipService
 */
actual class ZipService actual constructor() {
    
    /**
     * Extracts a ZIP file to a directory on iOS
     */
    actual suspend fun extractZip(
        zipFilePath: String,
        destinationPath: String,
        deleteZipAfterExtract: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val success = unzipFile(zipFilePath, destinationPath)
        
        if (success && deleteZipAfterExtract) {
            try {
                NSFileManager.defaultManager.removeItemAtPath(zipFilePath, null)
            } catch (e: Exception) {
                println("Failed to delete ZIP file after extraction: $e")
            }
        }
        
        success
    }
    
    /**
     * Creates a ZIP file from a directory on iOS
     */
    actual suspend fun createZip(directoryPath: String, zipFilePath: String): Boolean = 
        withContext(Dispatchers.IO) {
            createZipFile(directoryPath, zipFilePath)
        }
}