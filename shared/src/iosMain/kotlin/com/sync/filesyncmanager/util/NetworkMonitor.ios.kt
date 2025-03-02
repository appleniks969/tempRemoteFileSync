package com.sync.filesyncmanager.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityFlags
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkFlagsConnectionRequired
import platform.SystemConfiguration.kSCNetworkFlagsReachable
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsIsWWAN

/**
 * iOS implementation of NetworkMonitor
 */
@OptIn(ExperimentalForeignApi::class)
actual class NetworkMonitor {
    
    /**
     * Checks if any network is available on iOS
     * @return true if any network is available, false otherwise
     */
    actual suspend fun isNetworkAvailable(): Boolean = withContext(Dispatchers.IO) {
        val reachability = SCNetworkReachabilityCreateWithName(null, "www.apple.com") ?: return@withContext false
        
        val flags = ULongArray(1)
        if (!SCNetworkReachabilityGetFlags(reachability, flags)) {
            return@withContext false
        }
        
        val reachableFlag = flags[0].toInt() and kSCNetworkFlagsReachable.toInt()
        val connectionRequiredFlag = flags[0].toInt() and kSCNetworkFlagsConnectionRequired.toInt()
        
        return@withContext (reachableFlag != 0) && (connectionRequiredFlag == 0)
    }
    
    /**
     * Checks if WiFi network is available on iOS
     * This is a simplified implementation - iOS doesn't provide an easy way to distinguish WiFi from cellular
     * @return true if WiFi is available (non-cellular connection), false otherwise
     */
    actual suspend fun isWifiAvailable(): Boolean = withContext(Dispatchers.IO) {
        val reachability = SCNetworkReachabilityCreateWithName(null, "www.apple.com") ?: return@withContext false
        
        val flags = ULongArray(1)
        if (!SCNetworkReachabilityGetFlags(reachability, flags)) {
            return@withContext false
        }
        
        // On iOS, we can check if the connection is WWAN (cellular)
        // If it's not WWAN and network is available, we assume it's WiFi
        val isReachable = (flags[0].toInt() and kSCNetworkFlagsReachable.toInt()) != 0
        val isCellular = (flags[0].toInt() and kSCNetworkReachabilityFlagsIsWWAN.toInt()) != 0
        
        return@withContext isReachable && !isCellular
    }
    
    /**
     * Checks if an unmetered network is available on iOS
     * This is a simplified implementation - assume WiFi is unmetered (not always true in reality)
     * @return true if an unmetered network is available, false otherwise
     */
    actual suspend fun isUnmeteredNetworkAvailable(): Boolean = isWifiAvailable()
}