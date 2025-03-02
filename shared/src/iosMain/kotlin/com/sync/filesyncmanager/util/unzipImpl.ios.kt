package com.sync.filesyncmanager.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.S_IRWXU
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

/**
 * Unzips a file on iOS using NSFileManager
 *
 * @param zipPath the path to the zip file
 * @param destinationPath the directory to extract to
 * @return true if extraction was successful, false otherwise
 */
@OptIn(ExperimentalForeignApi::class)
internal fun unzipFile(zipPath: String, destinationPath: String): Boolean {
    val fileManager = NSFileManager.defaultManager
    
    // Create the destination directory if it doesn't exist
    try {
        if (!fileManager.fileExistsAtPath(destinationPath)) {
            fileManager.createDirectoryAtPath(
                destinationPath,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }
    } catch (e: Exception) {
        println("Failed to create destination directory: $e")
        return false
    }
    
    // Unzip the file
    try {
        // Using process execution since NSFileManager doesn't have built-in zip/unzip
        // We'll use the unix `unzip` command which is available on iOS
        val process = platform.posix.popen("unzip -o \"$zipPath\" -d \"$destinationPath\"", "r")
        
        if (process == null) {
            println("Failed to open process for unzip command")
            return false
        }
        
        val exitCode = platform.posix.pclose(process)
        val success = exitCode == 0
        
        if (!success) {
            println("Failed to unzip file, exit code: $exitCode")
            return false
        }
        
        return true
    } catch (e: Exception) {
        println("Exception during unzip: $e")
        return false
    }
}

/**
 * Creates a zip file on iOS
 * 
 * @param sourcePath path to the directory to zip
 * @param zipPath path for the output zip file
 * @return true if successful, false otherwise
 */
@OptIn(ExperimentalForeignApi::class)
internal fun createZipFile(sourcePath: String, zipPath: String): Boolean {
    val fileManager = NSFileManager.defaultManager
    
    try {
        // Check if source exists
        if (!fileManager.fileExistsAtPath(sourcePath)) {
            println("Source directory does not exist")
            return false
        }
        
        // Create parent directories for zip file if needed
        val zipPathUrl = NSURL.fileURLWithPath(zipPath)
        val zipPathParent = zipPathUrl.URLByDeletingLastPathComponent?.path
        
        if (zipPathParent != null && !fileManager.fileExistsAtPath(zipPathParent)) {
            fileManager.createDirectoryAtPath(
                zipPathParent,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }
        
        // Using process execution since NSFileManager doesn't have built-in zip/unzip
        // We'll use the unix `zip` command which is available on iOS
        val currentDirectory = fileManager.currentDirectoryPath
        fileManager.changeCurrentDirectoryPath(sourcePath)
        
        val process = platform.posix.popen("zip -r \"$zipPath\" .", "r")
        
        if (process == null) {
            println("Failed to open process for zip command")
            fileManager.changeCurrentDirectoryPath(currentDirectory)
            return false
        }
        
        val exitCode = platform.posix.pclose(process)
        val success = exitCode == 0
        
        // Restore original directory
        fileManager.changeCurrentDirectoryPath(currentDirectory)
        
        if (!success) {
            println("Failed to create zip file, exit code: $exitCode")
            return false
        }
        
        return true
    } catch (e: Exception) {
        println("Exception during zip creation: $e")
        return false
    }
}