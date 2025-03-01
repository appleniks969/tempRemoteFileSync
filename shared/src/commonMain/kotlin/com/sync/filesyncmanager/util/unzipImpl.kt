package com.sync.filesyncmanager.util

internal expect suspend fun ZipService.unzipImpl(
    zipFilePath: String,
    destDirPath: String
): Boolean