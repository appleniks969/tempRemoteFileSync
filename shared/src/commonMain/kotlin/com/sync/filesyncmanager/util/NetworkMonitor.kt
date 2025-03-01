package com.sync.filesyncmanager.util

/**
 * Platform-specific network monitoring
 */
expect class NetworkMonitor {
    /**
     * Checks if any network is available
     */
    suspend fun isNetworkAvailable(): Boolean
    
    /**
     * Checks if WiFi is available
     */
    suspend fun isWifiAvailable(): Boolean
    
    /**
     * Checks if an unmetered connection is available
     */
    suspend fun isUnmeteredNetworkAvailable(): Boolean
}
