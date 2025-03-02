package com.sync.filesyncmanager.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.pclose
import platform.posix.popen

/**
 * iOS implementation for IO dispatcher
 */
actual val IODispatcher: CoroutineDispatcher = Dispatchers.IO

/**
 * iOS implementation of ZipService
 */
actual class ZipService actual constructor() {
    /**
     * Extracts a ZIP file to a directory on iOS
     */
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun extractZip(
        zipFilePath: String,
        destinationPath: String,
        deleteZipAfterExtract: Boolean,
    ): Boolean =
        withContext(IODispatcher) {
            val success = extractZipFile(zipFilePath, destinationPath)

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
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun createZip(
        directoryPath: String,
        zipFilePath: String,
    ): Boolean =
        withContext(IODispatcher) {
            compressToZip(directoryPath, zipFilePath)
        }

    /**
     * Extracts a ZIP file to a destination directory on iOS
     * Using shell commands since NSFileManager doesn't have built-in zip support
     *
     * @param zipFilePath Path to the ZIP file
     * @param destinationPath Directory to extract to
     * @return True if extraction was successful
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun extractZipFile(
        zipFilePath: String,
        destinationPath: String,
    ): Boolean {
        val fileManager = NSFileManager.defaultManager

        // Create the destination directory if it doesn't exist
        try {
            if (!fileManager.fileExistsAtPath(destinationPath)) {
                fileManager.createDirectoryAtPath(
                    destinationPath,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )
            }
        } catch (e: Exception) {
            println("Failed to create destination directory: $e")
            return false
        }

        // Unzip the file using shell command
        try {
            val command = "unzip -o \"$zipFilePath\" -d \"$destinationPath\""
            val process = popen(command, "r") ?: return false

            val exitCode = pclose(process)
            return exitCode == 0
        } catch (e: Exception) {
            println("Error during unzip: $e")
            return false
        }
    }

    /**
     * Creates a ZIP file from a directory on iOS
     * Using shell commands since NSFileManager doesn't have built-in zip support
     *
     * @param directoryPath Path to the directory to compress
     * @param zipFilePath Path for the output ZIP file
     * @return True if compression was successful
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun compressToZip(
        directoryPath: String,
        zipFilePath: String,
    ): Boolean {
        val fileManager = NSFileManager.defaultManager

        try {
            // Check if source directory exists
            if (!fileManager.fileExistsAtPath(directoryPath)) {
                println("Source directory does not exist: $directoryPath")
                return false
            }

            // Create parent directory for ZIP file if needed
            val zipPathUrl = NSURL.fileURLWithPath(zipFilePath)
            val zipParentPath = zipPathUrl.URLByDeletingLastPathComponent?.path

            if (zipParentPath != null && !fileManager.fileExistsAtPath(zipParentPath)) {
                fileManager.createDirectoryAtPath(
                    zipParentPath,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )
            }

            // Save current directory
            val currentDir = fileManager.currentDirectoryPath

            // Change to source directory to use relative paths in zip command
            fileManager.changeCurrentDirectoryPath(directoryPath)

            // Use zip command to create ZIP file
            val command = "zip -r \"$zipFilePath\" ."
            val process = popen(command, "r") ?: return false

            val exitCode = pclose(process)

            // Restore original directory
            fileManager.changeCurrentDirectoryPath(currentDir)

            return exitCode == 0
        } catch (e: Exception) {
            println("Error creating ZIP file: $e")
            return false
        }
    }
}
