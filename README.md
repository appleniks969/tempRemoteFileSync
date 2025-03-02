# RemoteFileSyncKmm

A Kotlin Multiplatform (KMM) library for reliable file synchronization between remote servers and local storage on Android and iOS platforms.

## Features

- **Cross-Platform**: Single codebase supporting both Android and iOS
- **Bidirectional Sync**: Download, upload, or automatic bidirectional synchronization
- **Conflict Resolution**: Multiple strategies (local wins, remote wins, newest wins)
- **Network Awareness**: Configurable network constraints (any, WiFi only, unmetered only)
- **Automatic Scheduling**: Background sync with configurable intervals and conditions
- **Caching**: Flexible caching strategies with size management
- **ZIP Support**: Automatic extraction of downloaded ZIP archives
- **Progress Tracking**: Detailed transfer progress information
- **Checksumming**: File integrity verification
- **Persistence**: Local metadata storage for sync state management

## Installation

### Gradle Setup (Android)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        // Your repositories
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.sync:filesyncmanager:1.0.0")
}
```

### Swift Package Manager (iOS)

```swift
// Package.swift
dependencies: [
    .package(url: "https://github.com/your-username/RemoteFileSyncKmm", from: "1.0.0")
]
```

Or integrate directly in your Xcode project through the Swift Package integration.

## Basic Usage

### Android

```kotlin
// Initialize the sync manager
val fileSyncManager = FileSyncManagerFactory().create(
    SyncConfig(
        baseUrl = "your-api-endpoint", // Client-specific API endpoint
        authToken = "your-auth-token",
        syncStrategy = SyncStrategy.BIDIRECTIONAL,
        networkConstraint = NetworkConstraint.WIFI_ONLY
    )
)

// Start sync for all files
lifecycleScope.launch {
    val result = fileSyncManager.syncAll()
    when (result.status) {
        SyncStatus.SUCCESS -> showSuccessMessage()
        SyncStatus.PARTIAL -> showPartialSuccessMessage(result.successCount, result.errorCount)
        SyncStatus.ERROR -> showErrorMessage(result.error)
    }
}

// Download a specific file
val downloadResult = fileSyncManager.downloadFile("document.pdf", "/path/to/local/storage/")
```

### iOS (Swift)

```swift
// Initialize the sync manager
let fileSyncManager = FileSyncManagerFactory().create(
    initialConfig: SyncConfig(
        baseUrl: "your-api-endpoint", // Client-specific API endpoint
        authToken: "your-auth-token",
        syncStrategy: SyncStrategy.bidirectional,
        networkConstraint: NetworkConstraint.wifiOnly
    )
)

// Start sync for all files
fileSyncManager.syncAll { result in
    switch result.status {
    case .success:
        showSuccessMessage()
    case .partial:
        showPartialSuccessMessage(result.successCount, result.errorCount)
    case .error:
        showErrorMessage(result.error)
    }
}

// Download a specific file
fileSyncManager.downloadFile(remoteId: "document.pdf", localPath: "/path/to/local/storage/")
```

## Advanced Configuration

```kotlin
val config = SyncConfig(
    baseUrl = "your-api-endpoint", // Client-specific API endpoint
    authToken = "your-auth-token",
    syncStrategy = SyncStrategy.BIDIRECTIONAL,
    networkConstraint = NetworkConstraint.WIFI_ONLY,
    cacheStrategy = CacheStrategy.PRIORITY_BASED,
    autoSyncInterval = 30, // minutes
    compressUploads = true,
    extractZips = true,
    maxRetries = 3,
    maxCacheSizeMb = 100
)
```

## Monitoring Sync Progress

```kotlin
fileSyncManager.syncProgress.collect { progress ->
    updateProgressUI(
        progress.filesCompleted,
        progress.totalFiles,
        progress.bytesTransferred,
        progress.totalBytes,
        progress.currentFile
    )
}
```

## Requirements

- Android: minSdk 26+ (Android 8.0 Oreo)
- iOS: iOS 14.0+
- Kotlin: 2.1.0+
- Kotlin Coroutines: 1.10.1+

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request