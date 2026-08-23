package com.tiendatech.mobile.core.network

sealed interface NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>
    data class HttpError(
        val statusCode: Int,
        val message: String
    ) : NetworkResult<Nothing>
    data class ConnectionError(
        val message: String,
        val cause: Throwable
    ) : NetworkResult<Nothing>
    data class Timeout(
        val message: String,
        val cause: Throwable
    ) : NetworkResult<Nothing>
    data class UnexpectedError(
        val message: String,
        val cause: Throwable
    ) : NetworkResult<Nothing>
}
