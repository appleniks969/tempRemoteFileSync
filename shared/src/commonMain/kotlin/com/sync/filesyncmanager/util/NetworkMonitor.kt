package com.sync.filesyncmanager.util

/**
 * Platform-specific network monitoring
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class NetworkMonitor() {
    suspend fun isNetworkAvailable(): Boolean
    suspend fun isWifiAvailable(): Boolean
    suspend fun isUnmeteredNetworkAvailable(): Boolean
}
