package com.sync.filesyncmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.SyncStrategy
import com.sync.filesyncmanager.viewmodel.FileSyncViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: FileSyncViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create an initial sync config
        val initialConfig = SyncConfig(
            baseUrl = "https://api.example.com/files",
            syncStrategy = SyncStrategy.BIDIRECTIONAL
        )
        
        // Initialize the FileSyncManager in a coroutine
        lifecycleScope.launch {
            // Create a factory instance
            val syncManagerFactory = FileSyncManagerFactory()
            
            // Create the manager with initial config
            val syncManager = syncManagerFactory.create(initialConfig)
            
            // Pass the sync manager to the ViewModel
            viewModel.initialize(syncManager)
            
            // Set the UI content
            setContent {
                App(viewModel)
            }
        }
    }
}