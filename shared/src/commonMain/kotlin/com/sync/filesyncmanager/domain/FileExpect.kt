package com.sync.filesyncmanager.domain

/**
 * Gets the platform-specific cache directory path
 * @return the platform-specific cache directory path as a string
 */
internal expect fun getPlatformCacheDir(): String

/**
 * Gets the platform-specific files directory path
 * @return the platform-specific files directory path as a string
 */
internal expect fun getPlatformFilesDir(): String