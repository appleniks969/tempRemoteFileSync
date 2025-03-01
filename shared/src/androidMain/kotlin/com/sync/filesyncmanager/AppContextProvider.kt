package com.sync.filesyncmanager

import android.annotation.SuppressLint
import android.content.Context

/**
 * Provides application context across the library
 */
@SuppressLint("StaticFieldLeak")
object AppContextProvider {
    private lateinit var _context: Context

    val context: Context
        get() = _context

    fun initialize(context: Context) {
        _context = context.applicationContext
    }
}
