package com.sync.filesyncmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sync.filesyncmanager.api.FileSyncManagerFactory
import com.sync.filesyncmanager.data.local.DatabaseFactory
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.SyncStrategy
import com.sync.filesyncmanager.util.DataStoreProvider
import com.sync.filesyncmanager.util.FileUtils
import com.sync.filesyncmanager.util.NetworkMonitor
import com.sync.filesyncmanager.viewmodel.FileSyncViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: FileSyncViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create an initial sync config
        val initialConfig = SyncConfig(
            baseUrl = "https://api.example.com/files",
            syncStrategy = SyncStrategy.BIDIRECTIONAL
        )
        
        // Initialize the FileSyncManager
        val syncManager = FileSyncManagerFactory.create(initialConfig)
        
        // Pass the sync manager to the ViewModel
        viewModel.initialize(syncManager)

        setContent {
            App(viewModel)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(null)
}