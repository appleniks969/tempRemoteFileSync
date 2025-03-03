package com.sync.filesyncmanager.util

/**
 * Simple logging interface for multiplatform use
 */
interface Logger {
    /**
     * Logs a debug message
     */
    fun debug(message: String)

    /**
     * Logs an info message
     */
    fun info(message: String)

    /**
     * Logs a warning message
     */
    fun warn(message: String)

    /**
     * Logs an error message
     */
    fun error(
        message: String,
        throwable: Throwable? = null,
    )
}

/**
 * Basic implementation that logs to console
 */
class ConsoleLogger(
    private val tag: String,
) : Logger {
    override fun debug(message: String) {
        println("DEBUG [$tag]: $message")
    }

    override fun info(message: String) {
        println("INFO [$tag]: $message")
    }

    override fun warn(message: String) {
        println("WARN [$tag]: $message")
    }

    override fun error(
        message: String,
        throwable: Throwable?,
    ) {
        println("ERROR [$tag]: $message")
        throwable?.let { println(it.stackTraceToString()) }
    }
}
