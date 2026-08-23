package com.tiendatech.mobile.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

@Serializable
private data class ApiErrorResponse(
    val message: String? = null,
    val error: String? = null
)

suspend fun <T> safeApiCall(
    json: Json,
    apiCall: suspend () -> Response<T>
): NetworkResult<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                NetworkResult.Success(body)
            } else {
                NetworkResult.HttpError(
                    statusCode = response.code(),
                    message = "El servidor devolvió una respuesta vacía"
                )
            }
        } else {
            val errorBody = response.errorBody()?.string()
            val errorMessage = try {
                val errorObj = errorBody?.let { json.decodeFromString<ApiErrorResponse>(it) }
                errorObj?.message ?: errorObj?.error ?: errorBody
            } catch (_: Exception) {
                errorBody
            }
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: defaultHttpMessage(response.code())
            NetworkResult.HttpError(response.code(), errorMessage)
        }
    } catch (e: SocketTimeoutException) {
        NetworkResult.Timeout("Tiempo de espera agotado", e)
    } catch (e: IOException) {
        NetworkResult.ConnectionError("No se pudo conectar con el servidor", e)
    } catch (e: Exception) {
        NetworkResult.UnexpectedError("Ocurrió un error inesperado", e)
    }
}

private fun defaultHttpMessage(statusCode: Int): String = when (statusCode) {
    400 -> "La solicitud contiene datos inválidos"
    401 -> "La sesión no es válida o ha expirado"
    403 -> "No tienes permiso para realizar esta acción"
    404 -> "No se encontró el recurso solicitado"
    409 -> "La solicitud entra en conflicto con el estado actual"
    422 -> "No fue posible procesar los datos enviados"
    429 -> "Se realizaron demasiadas solicitudes. Inténtalo más tarde"
    in 500..599 -> "El servidor no está disponible temporalmente"
    else -> "La solicitud no pudo completarse"
}
