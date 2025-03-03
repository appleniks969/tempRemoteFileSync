package com.sync.filesyncmanager.domain.error

/**
 * Base exception class for sync errors
 */
open class SyncException(
    message: String,
    val errorCode: Int = ErrorCode.UNKNOWN,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Exception for network-related errors
 */
class NetworkException(
    message: String,
    errorCode: Int = ErrorCode.NETWORK_ERROR,
    cause: Throwable? = null,
) : SyncException(message, errorCode, cause)

/**
 * Exception for file-related errors
 */
class FileException(
    message: String,
    errorCode: Int = ErrorCode.FILE_ERROR,
    cause: Throwable? = null,
) : SyncException(message, errorCode, cause)

/**
 * Exception for authentication errors
 */
class AuthException(
    message: String,
    errorCode: Int = ErrorCode.AUTH_ERROR,
    cause: Throwable? = null,
) : SyncException(message, errorCode, cause)

/**
 * Constants for error codes
 */
object ErrorCode {
    const val UNKNOWN = 1000
    const val NETWORK_ERROR = 1001
    const val FILE_ERROR = 1002
    const val AUTH_ERROR = 1003
    const val FILE_NOT_FOUND = 1004
    const val SERVER_ERROR = 1005
    const val DISK_FULL = 1006
    const val FILE_TOO_LARGE = 1007
    const val PERMISSION_DENIED = 1008
    const val TIMEOUT = 1009
    const val CONFLICT = 1010
}
