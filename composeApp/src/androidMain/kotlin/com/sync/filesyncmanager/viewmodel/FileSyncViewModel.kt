package com.sync.filesyncmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.filesyncmanager.api.FileSyncManager
import com.sync.filesyncmanager.domain.FileMetadata
import com.sync.filesyncmanager.domain.SyncConfig
import com.sync.filesyncmanager.domain.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for interacting with the FileSyncManager
 */
class FileSyncViewModel : ViewModel() {
    private var syncManager: FileSyncManager? = null
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Initial)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _filesList = MutableStateFlow<List<FileMetadata>>(emptyList())
    val filesList: StateFlow<List<FileMetadata>> = _filesList.asStateFlow()
    
    private val _config = MutableStateFlow<SyncConfig?>(null)
    val config: StateFlow<SyncConfig?> = _config.asStateFlow()
    
    /**
     * Initialize the ViewModel with a FileSyncManager instance
     */
    fun initialize(manager: FileSyncManager) {
        syncManager = manager
        viewModelScope.launch {
            // Get the current configuration
            _config.value = manager.getConfig()
            
            // Start observing files
            observeFiles()
        }
    }
    
    /**
     * Observe file metadata changes
     */
    private fun observeFiles() {
        viewModelScope.launch {
            syncManager?.observeAllFileMetadata()?.collect { files ->
                _filesList.value = files
            }
        }
    }
    
    /**
     * Trigger a manual synchronization
     */
    fun syncFiles() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            
            syncManager?.let { manager ->
                val result = manager.syncAll()
                _syncState.value = when {
                    result.failedCount > 0 -> SyncState.Error("Failed to sync ${result.failedCount} files")
                    result.conflictCount > 0 -> SyncState.Conflict("${result.conflictCount} files have conflicts")
                    else -> SyncState.Success("Successfully synced ${result.successCount} files")
                }
            } ?: run {
                _syncState.value = SyncState.Error("FileSyncManager not initialized")
            }
        }
    }
    
    /**
     * Add a file to be synced
     */
    fun addFile(localPath: String, remoteUrl: String) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            
            syncManager?.let { manager ->
                val result = manager.addFile(localPath, remoteUrl)
                _syncState.value = when (result) {
                    is SyncResult.Success -> SyncState.Success("File added successfully")
                    is SyncResult.Error -> SyncState.Error(result.errorMessage)
                    is SyncResult.Conflict -> SyncState.Conflict("File has conflicts")
                }
            } ?: run {
                _syncState.value = SyncState.Error("FileSyncManager not initialized")
            }
        }
    }
    
    /**
     * Download a file
     */
    fun downloadFile(fileId: String) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            
            syncManager?.let { manager ->
                val result = manager.downloadFile(fileId)
                _syncState.value = when (result) {
                    is SyncResult.Success -> SyncState.Success("File downloaded successfully")
                    is SyncResult.Error -> SyncState.Error(result.errorMessage)
                    is SyncResult.Conflict -> SyncState.Conflict("File has conflicts")
                }
            } ?: run {
                _syncState.value = SyncState.Error("FileSyncManager not initialized")
            }
        }
    }
    
    /**
     * Upload a file
     */
    fun uploadFile(fileId: String) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            
            syncManager?.let { manager ->
                val result = manager.uploadFile(fileId)
                _syncState.value = when (result) {
                    is SyncResult.Success -> SyncState.Success("File uploaded successfully")
                    is SyncResult.Error -> SyncState.Error(result.errorMessage)
                    is SyncResult.Conflict -> SyncState.Conflict("File has conflicts")
                }
            } ?: run {
                _syncState.value = SyncState.Error("FileSyncManager not initialized")
            }
        }
    }
    
    /**
     * Update sync configuration
     */
    fun updateConfig(newConfig: SyncConfig) {
        viewModelScope.launch {
            syncManager?.let { manager ->
                manager.updateConfig(newConfig)
                _config.value = newConfig
            }
        }
    }
    
    /**
     * Clear all sync state
     */
    fun clearState() {
        _syncState.value = SyncState.Initial
    }
    
    /**
     * States for sync operations
     */
    sealed class SyncState {
        object Initial : SyncState()
        object Syncing : SyncState()
        data class Success(val message: String) : SyncState()
        data class Error(val message: String) : SyncState()
        data class Conflict(val message: String) : SyncState()
    }
}