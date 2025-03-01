package com.sync.filesyncmanager.domain

// Platform-specific functions
internal expect fun getPlatformCacheDir(): String

internal  expect fun getPlatformFilesDir(): String