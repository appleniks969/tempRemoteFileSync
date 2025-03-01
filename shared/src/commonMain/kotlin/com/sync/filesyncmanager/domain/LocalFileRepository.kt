package com.sync.filesyncmanager.domain

import com.sync.filesyncmanager.util.FileService
import kotlinx.datetime.Instant

/**
 * Implementation of LocalFileRepository using com.sync.filesyncmanager.util.FileService (Okio-based)
 */
class LocalFileRepository(private val fileService: FileService) {

    suspend fun saveFile(fileId: String, filePath: String, data: ByteArray): Boolean {
        return fileService.writeFile(filePath, data)
    }

    suspend fun readFile(fileId: String, filePath: String): ByteArray? {
        return fileService.readFile(filePath)
    }

    suspend fun deleteFile(filePath: String): Boolean {
        return fileService.deleteFile(filePath)
    }

    suspend fun fileExists(filePath: String): Boolean {
        return fileService.fileExists(filePath)
    }

    suspend fun getFileChecksum(filePath: String): String? {
        return fileService.calculateChecksum(filePath)
    }

    suspend fun createDirectory(dirPath: String): Boolean {
        return fileService.createDirectory(dirPath)
    }

    suspend fun getFileSize(filePath: String): Long {
        return fileService.getFileSize(filePath)
    }

    suspend fun getFileLastModified(filePath: String): Instant {
        return fileService.getLastModified(filePath) ?: Instant.fromEpochMilliseconds(0)
    }

    suspend fun listFiles(dirPath: String): List<String> {
        return fileService.listFiles(dirPath)
    }

    suspend fun moveFile(sourcePath: String, destinationPath: String): Boolean {
        return fileService.moveFile(sourcePath, destinationPath)
    }

    suspend fun copyFile(sourcePath: String, destinationPath: String): Boolean {
        return fileService.copyFile(sourcePath, destinationPath)
    }

    suspend fun getTotalCacheSize(): Long {
        return fileService.getTotalDirectorySize(getPlatformCacheDir())
    }

    suspend fun clearCache(dirPath: String): Boolean {
        return fileService.clearDirectory(dirPath)
    }
}
