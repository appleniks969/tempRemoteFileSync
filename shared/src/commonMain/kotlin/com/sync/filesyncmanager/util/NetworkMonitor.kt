package com.sync.filesyncmanager.util

/**
 * Cross-platform network monitor to check connectivity status
 */
expect class NetworkMonitor {
    /**
     * Checks if any network is available
     * @return true if any network is available, false otherwise
     */
    suspend fun isNetworkAvailable(): Boolean

    /**
     * Checks if WiFi network is available
     * @return true if WiFi is available, false otherwise
     */
    suspend fun isWifiAvailable(): Boolean

    /**
     * Checks if an unmetered network is available
     * @return true if an unmetered network is available, false otherwise
     */
    suspend fun isUnmeteredNetworkAvailable(): Boolean
}
