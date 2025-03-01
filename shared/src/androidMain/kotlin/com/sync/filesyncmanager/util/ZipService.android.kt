package com.sync.filesyncmanager.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

internal actual suspend fun ZipService.unzipImpl(zipFilePath: String, destDirPath: String): Boolean = withContext(
    Dispatchers.IO) {
    try {
        ZipInputStream(FileInputStream(zipFilePath)).use { zipIn ->
            var zipEntry: ZipEntry? = zipIn.nextEntry
            val buffer = ByteArray(8192)

            while (zipEntry != null) {
                val entryPath = "$destDirPath/${zipEntry.name}"

                if (zipEntry.isDirectory) {
                    // Create directory
                    fileService.createDirectory(entryPath)
                } else {
                    // Create parent directories if they don't exist
                    val lastSlashIndex = entryPath.lastIndexOf('/')
                    if (lastSlashIndex > 0) {
                        val dirPath = entryPath.substring(0, lastSlashIndex)
                        fileService.createDirectory(dirPath)
                    }

                    // Extract file
                    FileOutputStream(entryPath).use { fos ->
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }

                zipIn.closeEntry()
                zipEntry = zipIn.nextEntry
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}