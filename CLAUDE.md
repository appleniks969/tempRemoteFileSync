# RemoteFileSyncKmm Development Guide

## Build Status

The library successfully compiles for Android. iOS builds require Xcode to be installed, which is expected behavior.

## Key Architecture Notes

- The library uses the expect/actual pattern for platform-specific implementations
- Common interfaces are defined in common code, with platform-specific implementations
- Room database is used for Android, while a custom implementation is provided for iOS
- NetworkMonitor and FileSystem functionality is platform-specific
- File operations are abstracted to work cross-platform

## Important Build Commands

### Build
```
./gradlew build
```

### Build just the shared library
```
./gradlew shared:build
```

### Run ktlint to check code style
```
./gradlew spotlessCheck
```

### Fix ktlint issues
```
./gradlew spotlessApply
```

## Project Structure

- **shared/** - Multiplatform library code shared between Android and iOS
  - **commonMain/** - Common Kotlin code for both platforms
  - **androidMain/** - Android-specific implementations
  - **iosMain/** - iOS-specific implementations

## Coding Conventions

- Use KotlinX libraries for multiplatform compatibility (datetime, coroutines, serialization)
- Follow expect/actual pattern for platform-specific implementations
- Use Flow for asynchronous data streams
- Follow SOLID principles and clean architecture

## Best Practices for KMM Development

1. **Interface-First Approach**: Define interfaces in common code, implement in platform-specific code
2. **Minimal Platform Dependencies**: Keep platform-specific code minimal and isolated
3. **Common Data Models**: Share data models across platforms using @Serializable annotations
4. **Dependency Injection**: Use factories to create platform-specific implementations
5. **Error Handling**: Provide consistent error handling across platforms
6. **Testing**: Write platform-agnostic tests in common code when possible
7. **File Operations**: Use abstracted file operations with platform-specific implementations
8. **Networking**: Use Ktor for cross-platform networking
9. **Database Access**: Use interfaces to abstract database implementations
10. **Multithreading**: Use coroutines and Dispatchers for cross-platform concurrency

## Library Architecture

1. **API Layer** (`api/`)
   - FileSyncManager interface and implementation

2. **Domain Layer** (`domain/`)
   - Models, repositories and business logic

3. **Data Layer** (`data/`)
   - Local data storage
   - Remote data access

4. **Utilities** (`util/`)
   - Platform-specific utilities
   - Helper functions