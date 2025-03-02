package com.sync.filesyncmanager

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.sync.filesyncmanager.util.DataStoreProvider
import com.sync.filesyncmanager.util.FileUtils

/**
 * Android-specific initializer for the FileSyncManager using ContentProvider
 * to automatically initialize when the app starts.
 */
class FileSyncManagerInitializer : ContentProvider() {
    
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
    }
    
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, 
                      selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    
    override fun getType(uri: Uri): String? = null
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    
    override fun update(uri: Uri, values: ContentValues?, selection: String?, 
                       selectionArgs: Array<out String>?): Int = 0
}