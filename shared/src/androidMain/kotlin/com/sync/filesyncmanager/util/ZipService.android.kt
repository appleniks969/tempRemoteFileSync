package com.sync.filesyncmanager.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android implementation for IO dispatcher
 */
actual val IODispatcher: CoroutineDispatcher = Dispatchers.IO

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
        deleteZipAfterExtract: Boolean,
    ): Boolean =
        withContext(IODispatcher) {
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
    actual suspend fun createZip(
        directoryPath: String,
        zipFilePath: String,
    ): Boolean =
        withContext(IODispatcher) {
            createZipFile(directoryPath, zipFilePath)
        }

    /**
     * Unzips a file on Android using Java IO
     *
     * @param zipPath the path to the zip file
     * @param destinationPath the directory to extract to
     * @return true if extraction was successful, false otherwise
     */
    private fun extractZipFile(
        zipPath: String,
        destinationPath: String,
    ): Boolean {
        try {
            val destDir = File(destinationPath)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            val buffer = ByteArray(1024)
            val zipFile = File(zipPath)
            val zis = ZipInputStream(BufferedInputStream(FileInputStream(zipFile)))

            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val newFile = File(destDir, zipEntry.name)

                // Create parent directories if needed
                val parentFile = newFile.parentFile
                if (parentFile != null && !parentFile.exists()) {
                    parentFile.mkdirs()
                }

                if (!zipEntry.isDirectory) {
                    val fos = FileOutputStream(newFile)
                    val bos = BufferedOutputStream(fos)

                    var len: Int
                    while (zis.read(buffer).also { len = it } > 0) {
                        bos.write(buffer, 0, len)
                    }

                    bos.flush()
                    bos.close()
                } else {
                    newFile.mkdirs()
                }

                zis.closeEntry()
                zipEntry = zis.nextEntry
            }

            zis.close()

            return true
        } catch (e: Exception) {
            println("Error extracting ZIP: ${e.message}")
            return false
        }
    }

    /**
     * Creates a zip file on Android
     *
     * @param sourcePath path to the directory to zip
     * @param zipPath path for the output zip file
     * @return true if successful, false otherwise
     */
    private fun createZipFile(
        sourcePath: String,
        zipPath: String,
    ): Boolean {
        try {
            val sourceDir = File(sourcePath)
            val zipFile = File(zipPath)

            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                return false
            }

            // Ensure parent directories exist
            zipFile.parentFile?.mkdirs()

            val bos = BufferedOutputStream(FileOutputStream(zipFile))
            val zos = ZipOutputStream(bos)

            addDirectoryToZip(sourceDir, sourceDir, zos)

            zos.close()
            bos.close()

            return true
        } catch (e: Exception) {
            println("Error creating ZIP: ${e.message}")
            return false
        }
    }

    /**
     * Helper method to recursively add directories and files to ZIP
     */
    private fun addDirectoryToZip(
        rootDir: File,
        sourceDir: File,
        zos: ZipOutputStream,
    ) {
        val files = sourceDir.listFiles() ?: return

        val buffer = ByteArray(1024)

        for (file in files) {
            if (file.isDirectory) {
                addDirectoryToZip(rootDir, file, zos)
                continue
            }

            val bis = BufferedInputStream(FileInputStream(file))
            val entryPath =
                file.absolutePath
                    .removePrefix(rootDir.absolutePath)
                    .removePrefix("/") // Remove leading slash

            val entry = ZipEntry(entryPath)
            zos.putNextEntry(entry)

            var len: Int
            while (bis.read(buffer).also { len = it } > 0) {
                zos.write(buffer, 0, len)
            }

            bis.close()
            zos.closeEntry()
        }
    }
}
