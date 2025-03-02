package com.sync.filesyncmanager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sync.filesyncmanager.domain.FileMetadata
import com.sync.filesyncmanager.domain.SyncStatus
import com.sync.filesyncmanager.viewmodel.FileSyncViewModel
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import remotefilesynckmm.composeapp.generated.resources.Res
import remotefilesynckmm.composeapp.generated.resources.compose_multiplatform

@Composable
fun App(viewModel: FileSyncViewModel?) {
    MaterialTheme {
        val syncState by viewModel?.syncState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(FileSyncViewModel.SyncState.Initial) }
        val filesList by viewModel?.filesList?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
        val config by viewModel?.config?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
        
        var showAddDialog by remember { mutableStateOf(false) }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("File Sync Manager") },
                    actions = {
                        IconButton(onClick = { viewModel?.syncFiles() }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync files")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Text("+")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Status card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    elevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sync Status",
                            style = MaterialTheme.typography.h6
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        when (syncState) {
                            is FileSyncViewModel.SyncState.Initial -> {
                                Text("Ready to sync")
                            }
                            is FileSyncViewModel.SyncState.Syncing -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Syncing files...")
                                }
                            }
                            is FileSyncViewModel.SyncState.Success -> {
                                Text(
                                    (syncState as FileSyncViewModel.SyncState.Success).message,
                                    color = Color.Green
                                )
                            }
                            is FileSyncViewModel.SyncState.Error -> {
                                Text(
                                    (syncState as FileSyncViewModel.SyncState.Error).message,
                                    color = Color.Red
                                )
                            }
                            is FileSyncViewModel.SyncState.Conflict -> {
                                Text(
                                    (syncState as FileSyncViewModel.SyncState.Conflict).message,
                                    color = Color.Yellow
                                )
                            }
                        }
                        
                        config?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Server: ${it.baseUrl}")
                            Text("Strategy: ${it.syncStrategy.name}")
                        }
                    }
                }
                
                // Files list
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (filesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No files to sync")
                    }
                } else {
                    LazyColumn {
                        items(filesList) { file ->
                            FileItem(
                                file = file,
                                onDownload = { viewModel?.downloadFile(file.fileId) },
                                onUpload = { viewModel?.uploadFile(file.fileId) }
                            )
                        }
                    }
                }
            }
            
            if (showAddDialog) {
                AddFileDialog(
                    onDismiss = { showAddDialog = false },
                    onAddFile = { localPath, remoteUrl ->
                        viewModel?.addFile(localPath, remoteUrl)
                        showAddDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun FileItem(
    file: FileMetadata,
    onDownload: () -> Unit,
    onUpload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Status: ${file.syncStatus.name}",
                    style = MaterialTheme.typography.caption
                )
                Text(
                    text = "Path: ${file.filePath}",
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Row {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = "Download",
                        tint = if (!file.isDownloaded) MaterialTheme.colors.primary else Color.Gray
                    )
                }
                IconButton(onClick = onUpload) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = "Upload",
                        tint = if (!file.isUploaded) MaterialTheme.colors.primary else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun AddFileDialog(
    onDismiss: () -> Unit,
    onAddFile: (String, String) -> Unit
) {
    var localPath by remember { mutableStateOf("") }
    var remoteUrl by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add File") },
        text = {
            Column {
                OutlinedTextField(
                    value = localPath,
                    onValueChange = { localPath = it },
                    label = { Text("Local File Path") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = { remoteUrl = it },
                    label = { Text("Remote URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddFile(localPath, remoteUrl) },
                enabled = localPath.isNotBlank() && remoteUrl.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview
@Composable
fun FileItemPreview() {
    MaterialTheme {
        FileItem(
            file = FileMetadata(
                fileId = "file1",
                fileName = "example.txt",
                filePath = "/path/to/example.txt",
                remoteUrl = "https://example.com/files/example.txt",
                lastModified = Instant.parse("2023-01-01T00:00:00Z"),
                fileSize = 1024,
                syncStatus = SyncStatus.SYNCED,
                isDownloaded = true,
                isUploaded = true
            ),
            onDownload = {},
            onUpload = {}
        )
    }
}