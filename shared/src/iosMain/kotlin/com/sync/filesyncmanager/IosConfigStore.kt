package com.sync.filesyncmanager

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation for storing configuration
 */
class IosConfigStore {
    private val userDefaults = NSUserDefaults.standardUserDefaults
    
    fun getConfigData(key: String): String? {
        return userDefaults.stringForKey(key)
    }
    
    fun setConfigData(key: String, value: String) {
        userDefaults.setObject(value, key)
        userDefaults.synchronize()
    }
}