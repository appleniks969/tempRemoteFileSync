package com.sync.filesyncmanager.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Cross-platform service for handling ZIP file operations
 */
expect class ZipService() {
    /**
     * Extracts a ZIP file to a directory
     *
     * @param zipFilePath path to the ZIP file
     * @param destinationPath path to extract to
     * @param deleteZipAfterExtract whether to delete the ZIP file after extraction
     * @return true if extraction was successful
     */
    suspend fun extractZip(
        zipFilePath: String,
        destinationPath: String,
        deleteZipAfterExtract: Boolean = false,
    ): Boolean

    /**
     * Creates a ZIP file from a directory
     *
     * @param directoryPath path to the directory to compress
     * @param zipFilePath path for the output ZIP file
     * @return true if compression was successful
     */
    suspend fun createZip(
        directoryPath: String,
        zipFilePath: String,
    ): Boolean
}

/**
 * Platform-specific implementation for IO operations dispatcher
 */
expect val IODispatcher: CoroutineDispatcher
