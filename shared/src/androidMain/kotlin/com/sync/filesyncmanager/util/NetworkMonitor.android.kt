package com.sync.filesyncmanager.util

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import com.sync.filesyncmanager.AppContextProvider
import kotlinx.coroutines.withContext

/**
 * Android implementation of NetworkMonitor
 */
actual class NetworkMonitor {
    private val context: Context
        get() = AppContextProvider.context

    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    actual suspend fun isNetworkAvailable(): Boolean =
        withContext(IODispatcher) {
            val network = connectivityManager.activeNetwork ?: return@withContext false
            val capabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return@withContext false
            return@withContext capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    actual suspend fun isWifiAvailable(): Boolean =
        withContext(IODispatcher) {
            val network = connectivityManager.activeNetwork ?: return@withContext false
            val capabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return@withContext false
            return@withContext capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    actual suspend fun isUnmeteredNetworkAvailable(): Boolean =
        withContext(IODispatcher) {
            val network = connectivityManager.activeNetwork ?: return@withContext false
            val capabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return@withContext false
            return@withContext capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
}
