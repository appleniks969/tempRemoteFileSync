package com.sync.filesyncmanager.util

import kotlinx.coroutines.withContext

/**
 * Simplified iOS implementation of NetworkMonitor
 * This is a basic implementation that doesn't actually check network state
 * but provides the necessary API for the app to compile and run.
 */
actual class NetworkMonitor {
    /**
     * Checks if any network is available on iOS
     * Note: This is a simplified implementation that always returns true
     * @return true (always available in this implementation)
     */
    actual suspend fun isNetworkAvailable(): Boolean =
        withContext(IODispatcher) {
            // For simplicity, we assume network is available
            // A real implementation would check actual network state
            true
        }

    /**
     * Checks if WiFi network is available on iOS
     * Note: This is a simplified implementation that always returns true
     * @return true (always available in this implementation)
     */
    actual suspend fun isWifiAvailable(): Boolean =
        withContext(IODispatcher) {
            // For simplicity, we assume WiFi is available
            // A real implementation would check actual WiFi state
            true
        }

    /**
     * Checks if an unmetered network is available on iOS
     * Note: This is a simplified implementation that always returns true
     * @return true (always available in this implementation)
     */
    actual suspend fun isUnmeteredNetworkAvailable(): Boolean =
        withContext(IODispatcher) {
            // For simplicity, we assume unmetered network is available
            // A real implementation would check actual network metering state
            true
        }
}
