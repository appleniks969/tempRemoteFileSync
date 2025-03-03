package com.sync.filesyncmanager.api.network

import com.sync.filesyncmanager.domain.NetworkType
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.util.NetworkMonitor

/**
 * Manages network state and availability checks
 */
interface NetworkManager {
    /**
     * Checks if network is available based on the provided sync configuration
     */
    suspend fun isNetworkSuitable(config: SyncConfig): Boolean
}

/**
 * Implementation of NetworkManager
 */
class NetworkManagerImpl(
    private val networkMonitor: NetworkMonitor,
) : NetworkManager {
    override suspend fun isNetworkSuitable(config: SyncConfig): Boolean =
        when (config.networkType) {
            NetworkType.ANY -> networkMonitor.isNetworkAvailable()
            NetworkType.WIFI_ONLY -> networkMonitor.isWifiAvailable()
            NetworkType.UNMETERED_ONLY -> networkMonitor.isUnmeteredNetworkAvailable()
            NetworkType.NONE -> true // Offline mode - allow operations regardless of network
        }
}
