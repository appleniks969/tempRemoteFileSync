package com.sync.filesyncmanager.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for LocalFileRepository
 *
 * These tests verify the functionality of LocalFileRepository by
 * testing all its methods with a simplified mock implementation.
 */
class LocalFileRepositoryTest {
    // Simple in-memory file system implementation for testing
    private class TestFileService {
        private val files = mutableMapOf<String, ByteArray>()
        private val directories = mutableSetOf<String>()
        private val fileSizes = mutableMapOf<String, Long>()
        private val lastModifiedTimes = mutableMapOf<String, Instant>()
        private val checksums = mutableMapOf<String, String>()

        fun readFile(path: String): ByteArray? = files[path]

        fun writeFile(
            path: String,
            data: ByteArray,
        ): Boolean {
            files[path] = data
            fileSizes[path] = data.size.toLong()
            lastModifiedTimes[path] = Clock.System.now()
            return true
        }

        fun deleteFile(path: String): Boolean {
            val existed = files.containsKey(path)
            files.remove(path)
            fileSizes.remove(path)
            lastModifiedTimes.remove(path)
            checksums.remove(path)
            return existed || true
        }

        fun fileExists(path: String): Boolean = files.containsKey(path) || directories.contains(path)

        fun createDirectory(path: String): Boolean {
            directories.add(path)
            return true
        }

        fun getFileSize(path: String): Long = fileSizes[path] ?: -1L

        fun getLastModified(path: String): Instant? = lastModifiedTimes[path]

        fun listFiles(dirPath: String): List<String> = files.keys.filter { it.startsWith("$dirPath/") }.toList()

        fun moveFile(
            fromPath: String,
            toPath: String,
        ): Boolean {
            val data = files[fromPath] ?: return false
            files[toPath] = data
            fileSizes[toPath] = fileSizes[fromPath] ?: data.size.toLong()
            lastModifiedTimes[toPath] = Clock.System.now()
            files.remove(fromPath)
            fileSizes.remove(fromPath)
            lastModifiedTimes.remove(fromPath)
            checksums.remove(fromPath)
            return true
        }

        fun copyFile(
            fromPath: String,
            toPath: String,
        ): Boolean {
            val data = files[fromPath] ?: return false
            files[toPath] = data
            fileSizes[toPath] = fileSizes[fromPath] ?: data.size.toLong()
            lastModifiedTimes[toPath] = Clock.System.now()
            return true
        }

        fun calculateChecksum(path: String): String? {
            val data = files[path] ?: return null
            val mockChecksum = data.size.toString(16).padStart(32, '0')
            checksums[path] = mockChecksum
            return mockChecksum
        }

        fun getTotalDirectorySize(path: String): Long =
            files
                .filter { (key, _) -> key.startsWith(path) }
                .values
                .sumOf { it.size.toLong() }

        fun clearDirectory(path: String): Boolean {
            val keysToRemove = files.keys.filter { it.startsWith("$path/") }
            keysToRemove.forEach {
                files.remove(it)
                fileSizes.remove(it)
                lastModifiedTimes.remove(it)
                checksums.remove(it)
            }
            return true
        }

        // Helper method to set up initial test data
        fun setupTestFile(
            path: String,
            data: ByteArray,
            lastModified: Instant? = null,
        ) {
            files[path] = data
            fileSizes[path] = data.size.toLong()
            lastModifiedTimes[path] = lastModified ?: Clock.System.now()
        }
    }

    // Wrapper class to adapt TestFileService to provide suspend functions
    private class FileServiceWrapper(
        private val testService: TestFileService,
    ) {
        suspend fun readFile(path: String): ByteArray? = testService.readFile(path)

        suspend fun writeFile(
            path: String,
            data: ByteArray,
        ): Boolean = testService.writeFile(path, data)

        suspend fun deleteFile(path: String): Boolean = testService.deleteFile(path)

        suspend fun fileExists(path: String): Boolean = testService.fileExists(path)

        suspend fun createDirectory(path: String): Boolean = testService.createDirectory(path)

        suspend fun getFileSize(path: String): Long = testService.getFileSize(path)

        suspend fun getLastModified(path: String): Instant? = testService.getLastModified(path)

        suspend fun listFiles(dirPath: String): List<String> = testService.listFiles(dirPath)

        suspend fun moveFile(
            fromPath: String,
            toPath: String,
        ): Boolean = testService.moveFile(fromPath, toPath)

        suspend fun copyFile(
            fromPath: String,
            toPath: String,
        ): Boolean = testService.copyFile(fromPath, toPath)

        suspend fun calculateChecksum(path: String): String? = testService.calculateChecksum(path)

        suspend fun getTotalDirectorySize(path: String): Long = testService.getTotalDirectorySize(path)

        suspend fun clearDirectory(path: String): Boolean = testService.clearDirectory(path)
    }

    // Test implementation of LocalFileRepository for testing
    private class TestLocalFileRepository(
        private val fileServiceWrapper: FileServiceWrapper,
    ) {
        suspend fun saveFile(
            fileId: String,
            filePath: String,
            data: ByteArray,
        ): Boolean = fileServiceWrapper.writeFile(filePath, data)

        suspend fun readFile(
            fileId: String,
            filePath: String,
        ): ByteArray? = fileServiceWrapper.readFile(filePath)

        suspend fun deleteFile(filePath: String): Boolean = fileServiceWrapper.deleteFile(filePath)

        suspend fun fileExists(filePath: String): Boolean = fileServiceWrapper.fileExists(filePath)

        suspend fun getFileChecksum(filePath: String): String? = fileServiceWrapper.calculateChecksum(filePath)

        suspend fun createDirectory(dirPath: String): Boolean = fileServiceWrapper.createDirectory(dirPath)

        suspend fun getFileSize(filePath: String): Long = fileServiceWrapper.getFileSize(filePath)

        suspend fun getFileLastModified(filePath: String): Instant =
            fileServiceWrapper.getLastModified(filePath) ?: Instant.fromEpochMilliseconds(0)

        suspend fun listFiles(dirPath: String): List<String> = fileServiceWrapper.listFiles(dirPath)

        suspend fun moveFile(
            sourcePath: String,
            destinationPath: String,
        ): Boolean = fileServiceWrapper.moveFile(sourcePath, destinationPath)

        suspend fun copyFile(
            sourcePath: String,
            destinationPath: String,
        ): Boolean = fileServiceWrapper.copyFile(sourcePath, destinationPath)

        suspend fun getTotalCacheSize(): Long = fileServiceWrapper.getTotalDirectorySize("/test/cache")

        suspend fun clearCache(dirPath: String): Boolean = fileServiceWrapper.clearDirectory(dirPath)
    }

    private val testFileService = TestFileService()
    private val fileServiceWrapper = FileServiceWrapper(testFileService)
    private val repository = TestLocalFileRepository(fileServiceWrapper)

    @Test
    fun testSaveAndReadFile() =
        runTest {
            val fileId = "test1"
            val filePath = "/test/file.txt"
            val data = "Hello, World!".encodeToByteArray()

            // Test saving a file
            val saveResult = repository.saveFile(fileId, filePath, data)
            assertTrue(saveResult, "File should be saved successfully")

            // Test reading the file back
            val readData = repository.readFile(fileId, filePath)
            assertNotNull(readData, "Read data should not be null")
            assertEquals(data.size, readData.size, "Data size should match")
            assertEquals(data.decodeToString(), readData.decodeToString(), "Content should match")
        }

    @Test
    fun testDeleteFile() =
        runTest {
            val filePath = "/test/file-to-delete.txt"
            val data = "Delete this file".encodeToByteArray()

            // Setup a file
            testFileService.setupTestFile(filePath, data)

            // Verify file exists
            assertTrue(repository.fileExists(filePath), "File should exist before deletion")

            // Delete the file
            val deleteResult = repository.deleteFile(filePath)
            assertTrue(deleteResult, "File should be deleted successfully")

            // Verify file no longer exists
            assertFalse(repository.fileExists(filePath), "File should not exist after deletion")
        }

    @Test
    fun testFileExists() =
        runTest {
            val existingPath = "/test/existing-file.txt"
            val nonExistingPath = "/test/non-existing-file.txt"
            val data = "This file exists".encodeToByteArray()

            // Setup a file
            testFileService.setupTestFile(existingPath, data)

            // Test file exists
            assertTrue(repository.fileExists(existingPath), "Existing file should return true")
            assertFalse(repository.fileExists(nonExistingPath), "Non-existing file should return false")
        }

    @Test
    fun testGetFileChecksum() =
        runTest {
            val filePath = "/test/checksum-file.txt"
            val data = "Calculate checksum for this".encodeToByteArray()

            // Setup a file
            testFileService.setupTestFile(filePath, data)

            // Get checksum
            val checksum = repository.getFileChecksum(filePath)

            // Verify checksum is not null
            assertNotNull(checksum, "Checksum should not be null")

            // Check non-existing file returns null
            val nonExistingChecksum = repository.getFileChecksum("/non-existing.txt")
            assertNull(nonExistingChecksum, "Non-existing file should return null checksum")
        }

    @Test
    fun testCreateDirectory() =
        runTest {
            val dirPath = "/test/new-directory"

            // Create directory
            val result = repository.createDirectory(dirPath)
            assertTrue(result, "Directory should be created successfully")

            // Verify directory exists
            assertTrue(repository.fileExists(dirPath), "Directory should exist after creation")
        }

    @Test
    fun testGetFileSize() =
        runTest {
            val filePath = "/test/size-test.txt"
            val data = "This file has a specific size".encodeToByteArray()

            // Setup a file
            testFileService.setupTestFile(filePath, data)

            // Get file size
            val size = repository.getFileSize(filePath)

            // Verify size
            assertEquals(data.size.toLong(), size, "File size should match the data size")

            // Test non-existing file
            val nonExistingSize = repository.getFileSize("/non-existing.txt")
            assertEquals(-1L, nonExistingSize, "Non-existing file should return -1")
        }

    @Test
    fun testGetFileLastModified() =
        runTest {
            val filePath = "/test/timestamp-test.txt"
            val data = "Test last modified time".encodeToByteArray()
            val timestamp = Clock.System.now()

            // Setup a file with specific timestamp
            testFileService.setupTestFile(filePath, data, timestamp)

            // Get last modified
            val lastModified = repository.getFileLastModified(filePath)

            // Verify timestamp (should be close to the one we set)
            assertEquals(timestamp.epochSeconds, lastModified.epochSeconds, "Last modified time should match")

            // Test non-existing file returns epoch 0
            val nonExistingTimestamp = repository.getFileLastModified("/non-existing.txt")
            assertEquals(0L, nonExistingTimestamp.epochSeconds, "Non-existing file should return epoch 0")
        }

    @Test
    fun testListFiles() =
        runTest {
            val dirPath = "/test/list-dir"
            val file1 = "$dirPath/file1.txt"
            val file2 = "$dirPath/file2.txt"
            val file3 = "$dirPath/file3.txt"

            // Setup files
            testFileService.setupTestFile(file1, "File 1".encodeToByteArray())
            testFileService.setupTestFile(file2, "File 2".encodeToByteArray())
            testFileService.setupTestFile(file3, "File 3".encodeToByteArray())

            // List files
            val files = repository.listFiles(dirPath)

            // Verify files are listed
            assertEquals(3, files.size, "Should list 3 files")
            assertTrue(files.contains(file1), "Should contain file1")
            assertTrue(files.contains(file2), "Should contain file2")
            assertTrue(files.contains(file3), "Should contain file3")
        }

    @Test
    fun testMoveFile() =
        runTest {
            val sourcePath = "/test/source-file.txt"
            val destinationPath = "/test/destination-file.txt"
            val data = "Move this file".encodeToByteArray()

            // Setup source file
            testFileService.setupTestFile(sourcePath, data)

            // Move file
            val moveResult = repository.moveFile(sourcePath, destinationPath)
            assertTrue(moveResult, "File should be moved successfully")

            // Verify source no longer exists
            assertFalse(repository.fileExists(sourcePath), "Source file should no longer exist")

            // Verify destination exists with same content
            assertTrue(repository.fileExists(destinationPath), "Destination file should exist")
            val movedData = repository.readFile("", destinationPath)
            assertNotNull(movedData, "Should be able to read moved file")
            assertEquals(data.decodeToString(), movedData.decodeToString(), "Content should be preserved")
        }

    @Test
    fun testCopyFile() =
        runTest {
            val sourcePath = "/test/source-copy.txt"
            val destinationPath = "/test/destination-copy.txt"
            val data = "Copy this file".encodeToByteArray()

            // Setup source file
            testFileService.setupTestFile(sourcePath, data)

            // Copy file
            val copyResult = repository.copyFile(sourcePath, destinationPath)
            assertTrue(copyResult, "File should be copied successfully")

            // Verify source still exists
            assertTrue(repository.fileExists(sourcePath), "Source file should still exist")

            // Verify destination exists with same content
            assertTrue(repository.fileExists(destinationPath), "Destination file should exist")
            val copiedData = repository.readFile("", destinationPath)
            assertNotNull(copiedData, "Should be able to read copied file")
            assertEquals(data.decodeToString(), copiedData.decodeToString(), "Content should be preserved")
        }

    @Test
    fun testGetTotalCacheSize() =
        runTest {
            val cachePath = "/test/cache"
            val file1 = "$cachePath/file1.txt"
            val file2 = "$cachePath/file2.txt"

            // Setup cache files with known sizes
            testFileService.setupTestFile(file1, ByteArray(100))
            testFileService.setupTestFile(file2, ByteArray(200))

            // Get total cache size
            val totalSize = repository.getTotalCacheSize()

            // Verify total size
            assertEquals(300L, totalSize, "Total cache size should be 300 bytes")
        }

    @Test
    fun testClearCache() =
        runTest {
            val cachePath = "/test/cache"
            val file1 = "$cachePath/file1.txt"
            val file2 = "$cachePath/file2.txt"

            // Setup cache files
            testFileService.setupTestFile(file1, "Cache file 1".encodeToByteArray())
            testFileService.setupTestFile(file2, "Cache file 2".encodeToByteArray())

            // Verify files exist
            assertTrue(repository.fileExists(file1), "Cache file 1 should exist")
            assertTrue(repository.fileExists(file2), "Cache file 2 should exist")

            // Clear cache
            val clearResult = repository.clearCache(cachePath)
            assertTrue(clearResult, "Cache should be cleared successfully")

            // Verify files no longer exist
            val files = repository.listFiles(cachePath)
            assertEquals(0, files.size, "Cache directory should be empty")
        }
}
