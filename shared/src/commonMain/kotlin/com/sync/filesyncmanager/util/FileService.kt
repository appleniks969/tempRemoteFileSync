package com.sync.filesyncmanager.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import okio.*
import okio.Path.Companion.toPath

/**
 * Cross-platform file service built with Okio
 */
class FileService(private val fileSystem: FileSystem) {

    /**
     * Reads a file from the given path
     */
    suspend fun readFile(path: String): ByteArray? = withContext(Dispatchers.Default) {
        try {
            val okioPath = path.toOkioPath()
            if (!fileSystem.exists(okioPath)) return@withContext null

            fileSystem.read(okioPath) {
                readByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Writes data to a file at the given path
     */
    suspend fun writeFile(path: String, data: ByteArray): Boolean =
        withContext(Dispatchers.Default) {
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
                false
            }
        }

    /**
     * Deletes a file at the given path
     */
    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val okioPath = path.toOkioPath()
            if (fileSystem.exists(okioPath)) {
                fileSystem.delete(okioPath)
                true
            } else {
                true // Consider it a success if the file already doesn't exist
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a file exists at the given path
     */
    suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.Default) {
        try {
            fileSystem.exists(path.toOkioPath())
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates a directory at the given path
     */
    suspend fun createDirectory(path: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val okioPath = path.toOkioPath()
            fileSystem.createDirectories(okioPath)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the size of a file in bytes
     */
    suspend fun getFileSize(path: String): Long = withContext(Dispatchers.Default) {
        try {
            val okioPath = path.toOkioPath()
            if (fileSystem.exists(okioPath)) {
                fileSystem.metadata(okioPath).size ?: -1L
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Gets the last modified timestamp of a file
     */
    suspend fun getLastModified(path: String): Instant? = withContext(Dispatchers.Default) {
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
            null
        }
    }

    /**
     * Lists all files in a directory
     */
    suspend fun listFiles(path: String): List<String> = withContext(Dispatchers.Default) {
        try {
            val okioPath = path.toOkioPath()
            if (fileSystem.exists(okioPath) && fileSystem.metadata(okioPath).isDirectory) {
                fileSystem.list(okioPath).map { it.toString() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Moves a file from one path to another
     */
    suspend fun moveFile(fromPath: String, toPath: String): Boolean =
        withContext(Dispatchers.Default) {
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
                false
            }
        }

    /**
     * Copies a file from one path to another
     */
    suspend fun copyFile(fromPath: String, toPath: String): Boolean =
        withContext(Dispatchers.Default) {
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
                false
            }
        }

    /**
     * Calculates the MD5 checksum of a file
     */
    suspend fun calculateChecksum(path: String): String? = withContext(Dispatchers.Default) {
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
            null
        }
    }

    /**
     * Gets the total size of all files in a directory
     */
    suspend fun getTotalDirectorySize(path: String): Long = withContext(Dispatchers.Default) {
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
            0L
        }
    }

    /**
     * Clears a directory by deleting all files
     */
    suspend fun clearDirectory(path: String): Boolean = withContext(Dispatchers.Default) {
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
            false
        }
    }

    /**
     * Helper extension to convert a String path to an Okio Path
     */
    private fun String.toOkioPath(): Path {
        return this.toPath()
    }
}
