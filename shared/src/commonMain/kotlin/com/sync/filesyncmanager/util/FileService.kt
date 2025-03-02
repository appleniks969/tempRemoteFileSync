package com.sync.filesyncmanager.util

import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import okio.Buffer
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.Path.Companion.toPath

/**
 * Cross-platform file service built with Okio
 */
class FileService(
    private val fileSystem: FileSystem,
) {
    /**
     * Reads a file from the given path
     */
    suspend fun readFile(path: String): ByteArray? =
        withContext(IODispatcher) {
            try {
                val okioPath = path.toOkioPath()
                if (!fileSystem.exists(okioPath)) return@withContext null

                fileSystem.read(okioPath) {
                    readByteArray()
                }
            } catch (e: Exception) {
                println("Error reading file: ${e.message}")
                null
            }
        }

    /**
     * Writes data to a file at the given path
     */
    suspend fun writeFile(
        path: String,
        data: ByteArray,
    ): Boolean =
        withContext(IODispatcher) {
            try {
                val okioPath = path.toOkioPath()

                // Create parent directory if it doesn't exist
                val parent = okioPath.parent
                if (parent != null && !fileSystem.exists(parent)) {
                    fileSystem.createDirectories(parent)
                }

                fileSystem.write(okioPath) {
                    write(data)
                    flush()
                }
                true
            } catch (e: Exception) {
                println("Error writing file: ${e.message}")
                false
            }
        }

    /**
     * Deletes a file at the given path
     */
    suspend fun deleteFile(path: String): Boolean =
        withContext(IODispatcher) {
            try {
                val okioPath = path.toOkioPath()
                if (fileSystem.exists(okioPath)) {
                    fileSystem.delete(okioPath)
                    true
                } else {
                    true // Consider it a success if the file already doesn't exist
                }
            } catch (e: Exception) {
                println("Error deleting file: ${e.message}")
                false
            }
        }

    /**
     * Checks if a file exists at the given path
     */
    suspend fun fileExists(path: String): Boolean =
        withContext(IODispatcher) {
            try {
                fileSystem.exists(path.toOkioPath())
            } catch (e: Exception) {
                println("Error checking if file exists: ${e.message}")
                false
            }
        }

    /**
     * Creates a directory at the given path
     */
    suspend fun createDirectory(path: String): Boolean =
        withContext(IODispatcher) {
            try {
                val okioPath = path.toOkioPath()
                fileSystem.createDirectories(okioPath)
                true
            } catch (e: Exception) {
                println("Error creating directory: ${e.message}")
                false
            }
        }

    /**
     * Gets the size of a file in bytes
     */
    suspend fun getFileSize(path: String): Long =
        withContext(IODispatcher) {
            try {
                val okioPath = path.toOkioPath()
                if (fileSystem.exists(okioPath)) {
                    fileSystem.metadata(okioPath).size ?: -1L
                } else {
                    -1L
                }
            } catch (e: Exception) {
                println("Error getting file size: ${e.message}")
                -1L
            }
        }

    /**
     * Gets the last modified timestamp of a file
     */
    suspend fun getLastModified(path: String): Instant? =
        withContext(IODispatcher) {
            try {
                val okioPath = path.toOkioPath()
                if (fileSystem.exists(okioPath)) {
                    val modifiedAtMillis = fileSystem.metadata(okioPath).lastModifiedAtMillis
                    if (modifiedAtMillis != null) {
                        Instant.fromEpochMilliseconds(modifiedAtMillis)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                println("Error getting last modified time: ${e.message}")
                null
            }
        }

    /**
     * Lists all files in a directory
     */
    suspend fun listFiles(path: String): List<String> =
        withContext(IODispatcher) {
            try {
                val okioPath = path.toOkioPath()
                if (fileSystem.exists(okioPath) && fileSystem.metadata(okioPath).isDirectory) {
                    fileSystem.list(okioPath).map { it.toString() }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                println("Error listing files: ${e.message}")
                emptyList()
            }
        }

    /**
     * Moves a file from one path to another
     */
    suspend fun moveFile(
        fromPath: String,
        toPath: String,
    ): Boolean =
        withContext(IODispatcher) {
            try {
                val source = fromPath.toOkioPath()
                val target = toPath.toOkioPath()

                // Create parent directory if it doesn't exist
                val parent = target.parent
                if (parent != null && !fileSystem.exists(parent)) {
                    fileSystem.createDirectories(parent)
                }

                if (fileSystem.exists(source)) {
                    fileSystem.atomicMove(source, target)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                println("Error moving file: ${e.message}")
                false
            }
        }

    /**
     * Copies a file from one path to another
     */
    suspend fun copyFile(
        fromPath: String,
        toPath: String,
    ): Boolean =
        withContext(IODispatcher) {
            try {
                val source = fromPath.toOkioPath()
                val target = toPath.toOkioPath()

                // Create parent directory if it doesn't exist
                val parent = target.parent
                if (parent != null && !fileSystem.exists(parent)) {
                    fileSystem.createDirectories(parent)
                }

                if (fileSystem.exists(source)) {
                    fileSystem.copy(source, target)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                println("Error copying file: ${e.message}")
                false
            }
        }

    /**
     * Calculates the MD5 checksum of a file
     */
    suspend fun calculateChecksum(path: String): String? =
        withContext(IODispatcher) {
            try {
                val okioPath = path.toOkioPath()
                if (!fileSystem.exists(okioPath)) return@withContext null

                fileSystem.read(okioPath) {
                    val hashingSink = HashingSink.md5(Buffer())
                    val buffer = Buffer()
                    var bytesRead: Long

                    while (true) {
                        bytesRead = read(buffer, 8192L)
                        if (bytesRead <= 0) break

                        hashingSink.write(buffer, bytesRead)
                    }

                    hashingSink.hash.hex()
                }
            } catch (e: Exception) {
                println("Error calculating checksum: ${e.message}")
                null
            }
        }

    /**
     * Gets the total size of all files in a directory
     */
    suspend fun getTotalDirectorySize(path: String): Long =
        withContext(IODispatcher) {
            try {
                val okioPath = path.toOkioPath()
                if (!fileSystem.exists(okioPath)) return@withContext 0L

                var totalSize = 0L

                fun calculateSize(dir: Path) {
                    fileSystem.list(dir).forEach { subPath ->
                        val metadata = fileSystem.metadata(subPath)
                        if (metadata.isDirectory) {
                            calculateSize(subPath)
                        } else {
                            totalSize += metadata.size ?: 0L
                        }
                    }
                }

                if (fileSystem.metadata(okioPath).isDirectory) {
                    calculateSize(okioPath)
                } else {
                    totalSize = fileSystem.metadata(okioPath).size ?: 0L
                }

                totalSize
            } catch (e: Exception) {
                println("Error getting directory size: ${e.message}")
                0L
            }
        }

    /**
     * Clears a directory by deleting all files
     */
    suspend fun clearDirectory(path: String): Boolean =
        withContext(IODispatcher) {
            try {
                val okioPath = path.toOkioPath()
                if (!fileSystem.exists(okioPath)) return@withContext true

                if (fileSystem.metadata(okioPath).isDirectory) {
                    fileSystem.list(okioPath).forEach { subPath ->
                        val metadata = fileSystem.metadata(subPath)
                        if (metadata.isDirectory) {
                            clearDirectory(subPath.toString())
                            fileSystem.deleteRecursively(subPath)
                        } else {
                            fileSystem.delete(subPath)
                        }
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                println("Error clearing directory: ${e.message}")
                false
            }
        }

    /**
     * Helper extension to convert a String path to an Okio Path
     */
    private fun String.toOkioPath(): Path = this.toPath()
}

// NetworkMonitor and SyncScheduler are now defined in their own files

/**
 * Helper class for handling ZIP operations within FileService
 */
class ZipUtility(
    private val fileSystem: FileSystem,
    val fileService: FileService,
) {
    /**
     * Extracts a ZIP file to a destination directory
     *
     * @param zipFilePath Path to the ZIP file
     * @param destDirPath Path to the destination directory
     * @return Path to the extracted directory or null if extraction failed
     */
    suspend fun unzip(
        zipFilePath: String,
        destDirPath: String,
    ): String? =
        withContext(IODispatcher) {
            try {
                // Ensure destination directory exists
                fileService.createDirectory(destDirPath)

                // Use platform ZipService for extraction
                val zipService = ZipService()
                val success = zipService.extractZip(zipFilePath, destDirPath, false)

                if (success) destDirPath else null
            } catch (e: Exception) {
                println("Error unzipping file: ${e.message}")
                null
            }
        }

    /**
     * Creates a ZIP file from a directory
     *
     * @param directoryPath Path to the directory to zip
     * @param zipFilePath Path for the output ZIP file
     * @return Path to the created ZIP file or null if creation failed
     */
    suspend fun createZip(
        directoryPath: String,
        zipFilePath: String,
    ): String? =
        withContext(IODispatcher) {
            try {
                // Use platform ZipService for compression
                val zipService = ZipService()
                val success = zipService.createZip(directoryPath, zipFilePath)

                if (success) zipFilePath else null
            } catch (e: Exception) {
                println("Error creating zip file: ${e.message}")
                null
            }
        }

    /**
     * Checks if a file is a ZIP file based on its extension
     */
    fun isZipFile(filePath: String): Boolean = filePath.lowercase().endsWith(".zip")
}

/**
 * Platform-specific functions for file access are defined in FileExpect.kt
 */
