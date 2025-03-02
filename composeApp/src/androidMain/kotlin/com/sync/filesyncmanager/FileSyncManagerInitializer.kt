package com.sync.filesyncmanager

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.sync.filesyncmanager.util.DataStoreProvider
import com.sync.filesyncmanager.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android-specific initializer for the FileSyncManager using ContentProvider
 * to automatically initialize when the app starts.
 */
class FileSyncManagerInitializer : ContentProvider() {
    private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(): Boolean {
        val context = context ?: return false
        initialize(context)
        return true
    }

    private fun initialize(context: Context) {
        // Initialize other components
        AppContextProvider.initialize(context)
        DataStoreProvider.initialize(context)
        FileUtils.initialize(context)

        // Ensure required directories exist
        initializationScope.launch {
            try {
                val factory = FileSyncManagerFactory()
                factory.ensureRequiredDirectories()
            } catch (e: Exception) {
                // Log or handle initialization error
                e.printStackTrace()
            }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
