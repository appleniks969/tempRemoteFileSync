package com.sync.filesyncmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun app(viewModel: FileSyncViewModel?) {
    MaterialTheme {
        val syncState by viewModel?.syncState?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(FileSyncViewModel.SyncState.Initial) }
        val filesList by viewModel?.filesList?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
        val config by viewModel?.config?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

        var showAddDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                // Use a simple Box with Row instead of the experimental TopAppBar
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF6200EE))
                            .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "File Sync Manager",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        IconButton(onClick = { viewModel?.syncFiles() }) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Sync files",
                                tint = Color.White,
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Text("+")
                }
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
            ) {
                // Status card
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sync Status",
                            style = MaterialTheme.typography.titleLarge,
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
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Syncing files...")
                                }
                            }
                            is FileSyncViewModel.SyncState.Success -> {
                                Text(
                                    (syncState as FileSyncViewModel.SyncState.Success).message,
                                    color = Color.Green,
                                )
                            }
                            is FileSyncViewModel.SyncState.Error -> {
                                Text(
                                    (syncState as FileSyncViewModel.SyncState.Error).message,
                                    color = Color.Red,
                                )
                            }
                            is FileSyncViewModel.SyncState.Conflict -> {
                                Text(
                                    (syncState as FileSyncViewModel.SyncState.Conflict).message,
                                    color = Color.Yellow,
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
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (filesList.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No files to sync")
                    }
                } else {
                    LazyColumn {
                        items(filesList) { file ->
                            fileItem(
                                file = file,
                                onDownload = { viewModel?.downloadFile(file.fileId) },
                                onUpload = { viewModel?.uploadFile(file.fileId) },
                            )
                        }
                    }
                }
            }

            if (showAddDialog) {
                addFileDialog(
                    onDismiss = { showAddDialog = false },
                    onAddFile = { fileName, remoteUrl ->
                        viewModel?.addFileByName(fileName, remoteUrl)
                        showAddDialog = false
                    },
                )
            }
        }
    }
}

@Composable
fun fileItem(
    file: FileMetadata,
    onDownload: () -> Unit,
    onUpload: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Status: ${file.syncStatus.name}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Path: ${file.filePath}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = "Download",
                        tint = if (!file.isDownloaded) Color.Blue else Color.Gray,
                    )
                }
                IconButton(onClick = onUpload) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = "Upload",
                        tint = if (!file.isUploaded) Color.Blue else Color.Gray,
                    )
                }
            }
        }
    }
}

@Composable
fun addFileDialog(
    onDismiss: () -> Unit,
    onAddFile: (String, String) -> Unit,
) {
    var fileName by remember { mutableStateOf("") }
    var remoteUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add File") },
        text = {
            Column {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = { remoteUrl = it },
                    label = { Text("Remote URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddFile(fileName, remoteUrl) },
                enabled = fileName.isNotBlank() && remoteUrl.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Preview is removed since it's not available
@Composable
fun fileItemPreview() {
    MaterialTheme {
        fileItem(
            file =
                FileMetadata(
                    fileId = "file1",
                    fileName = "example.txt",
                    filePath = "/path/to/example.txt",
                    remoteUrl = "https://example.com/files/example.txt",
                    lastModified = Instant.parse("2023-01-01T00:00:00Z"),
                    fileSize = 1024,
                    syncStatus = SyncStatus.SYNCED,
                    isDownloaded = true,
                    isUploaded = true,
                ),
            onDownload = {},
            onUpload = {},
        )
    }
}
