package com.sync.filesyncmanager.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of ZipService
 */
actual class ZipService actual constructor() {
    
    /**
     * Extracts a ZIP file to a directory on Android
     */
    actual suspend fun extractZip(
        zipFilePath: String,
        destinationPath: String,
        deleteZipAfterExtract: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val extractSuccess = extractZipFile(zipFilePath, destinationPath)
        
        if (extractSuccess && deleteZipAfterExtract) {
            try {
                File(zipFilePath).delete()
            } catch (e: Exception) {
                println("Error deleting ZIP file: ${e.message}")
            }
        }
        
        extractSuccess
    }
    
    /**
     * Creates a ZIP file from a directory on Android
     */
    actual suspend fun createZip(directoryPath: String, zipFilePath: String): Boolean = 
        withContext(Dispatchers.IO) {
            createZipFile(directoryPath, zipFilePath)
        }
}