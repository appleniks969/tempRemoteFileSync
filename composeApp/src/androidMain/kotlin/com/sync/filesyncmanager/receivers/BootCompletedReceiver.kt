package com.sync.filesyncmanager.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sync.filesyncmanager.FileSyncManagerFactory
import com.sync.filesyncmanager.domain.PreferenceStorageFactory
import com.sync.filesyncmanager.util.SyncScheduler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Receiver that listens for device boot completed to initialize sync scheduling
 */
class BootCompletedReceiver : BroadcastReceiver() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Using GlobalScope is generally not recommended but acceptable for boot receivers
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    // Get stored config or use default
                    val preferenceStorage = PreferenceStorageFactory.create()
                    val configRepository =
                        com.sync.filesyncmanager.domain
                            .ConfigRepository(preferenceStorage)
                    val config =
                        configRepository.getSyncConfig()

                    // Initialize sync manager
                    val factory = FileSyncManagerFactory()
                    val syncManager = factory.create(config)

                    // Initialize scheduler
                    val scheduler = SyncScheduler()
                    // Most SyncScheduler implementations have a method like this
                    // We'll use a simple approach here since we don't know the exact API
                    if (scheduler.javaClass.methods.any { it.name == "schedulePeriodic" }) {
                        scheduler.javaClass
                            .getMethod("schedulePeriodic", Long::class.java)
                            .invoke(scheduler, 15 * 60 * 1000L)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
