package com.sync.filesyncmanager.domain


/**
 * Factory for creating platform-specific PreferenceStorage implementations
 */
expect object PreferenceStorageFactory {
    fun create(): PreferenceStorage
}