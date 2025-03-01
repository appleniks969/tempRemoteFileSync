package com.sync.filesyncmanager.util

import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.Network.nw_interface_type_wifi
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_t
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.CPointer

/**
 * Platform-specific network monitoring implementation for iOS
 * 
 * Uses Apple's Network framework to check network connectivity
 */
actual class NetworkMonitor actual constructor() {
    /**
     * Checks if any network is available
     */
    actual suspend fun isNetworkAvailable(): Boolean = suspendCoroutine { continuation ->
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(monitor) { path ->
            val isAvailable = nw_path_get_status(path) == nw_path_status_satisfied
            continuation.resume(isAvailable)
        }
        nw_path_monitor_start(monitor)
    }

    /**
     * Checks if WiFi is available
     */
    actual suspend fun isWifiAvailable(): Boolean = suspendCoroutine { continuation ->
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(monitor) { path ->
            val hasWifi = nw_path_uses_interface_type(path, nw_interface_type_wifi)
            continuation.resume(hasWifi)
        }
        nw_path_monitor_start(monitor)
    }

    /**
     * Checks if an unmetered connection is available
     * 
     * On iOS, we use the "expensive" flag as an indicator of a metered connection
     */
    actual suspend fun isUnmeteredNetworkAvailable(): Boolean = suspendCoroutine { continuation ->
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(monitor) { path ->
            val isAvailable = nw_path_get_status(path) == nw_path_status_satisfied
            val isExpensive = nw_path_is_expensive(path)
            continuation.resume(isAvailable && !isExpensive)
        }
        nw_path_monitor_start(monitor)
    }
}