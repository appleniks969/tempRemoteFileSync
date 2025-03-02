package com.sync.filesyncmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.SyncStrategy
import com.sync.filesyncmanager.viewmodel.FileSyncViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel: FileSyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set UI content immediately to avoid ANR
        setContent {
            app(viewModel)
        }

        // Create an initial sync config
        val initialConfig =
            SyncConfig(
                baseUrl = "https://api.example.com/files",
                syncStrategy = SyncStrategy.BIDIRECTIONAL,
                authToken = null, // Add auth token if needed
            )

        // Create exception handler
        val exceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                // Log the error
                throwable.printStackTrace()

                // Update the UI to show error
                viewModel.clearState()
            }

        // Initialize the FileSyncManager in a coroutine
        lifecycleScope.launch(exceptionHandler) {
            try {
                withContext(Dispatchers.IO) {
                    // Create a factory instance
                    val syncManagerFactory = FileSyncManagerFactory()

                    // Create the manager with initial config
                    val syncManager = syncManagerFactory.create(initialConfig)

                    // Pass the sync manager to the ViewModel
                    withContext(Dispatchers.Main) {
                        viewModel.initialize(syncManager)
                    }
                }
            } catch (e: Exception) {
                // Handle initialization error and log it
                e.printStackTrace()

                // Clear the state to show initial state
                viewModel.clearState()
            }
        }
    }
}
