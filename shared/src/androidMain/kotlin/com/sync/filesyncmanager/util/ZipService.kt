package com.sync.filesyncmanager.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Android-specific implementation for unzipping files
 */
fun unzipFile(zipFilePath: String, destDirPath: String): Boolean {
    try {
        val destDir = File(destDirPath)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        
        ZipInputStream(FileInputStream(zipFilePath)).use { zipIn ->
            var zipEntry: ZipEntry? = zipIn.nextEntry
            val buffer = ByteArray(8192)

            while (zipEntry != null) {
                val newFile = File(destDir, zipEntry.name)
                
                // Create parent directories if needed
                val parentFile = newFile.parentFile
                if (parentFile != null && !parentFile.exists()) {
                    parentFile.mkdirs()
                }
                
                if (!zipEntry.isDirectory) {
                    FileOutputStream(newFile).use { fos ->
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                } else {
                    newFile.mkdirs()
                }

                zipIn.closeEntry()
                zipEntry = zipIn.nextEntry
            }
        }
        return true
    } catch (e: Exception) {
        println("Error unzipping file: ${e.message}")
        return false
    }
}