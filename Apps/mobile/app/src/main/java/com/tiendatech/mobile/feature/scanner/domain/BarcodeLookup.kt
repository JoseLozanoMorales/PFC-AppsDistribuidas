package com.tiendatech.mobile.feature.scanner.domain

sealed interface BarcodeLookupResult {
    data class Found(val productId: Long) : BarcodeLookupResult
    data object NotFound : BarcodeLookupResult
    data object BackendUnavailable : BarcodeLookupResult
}

fun interface ProductLookupByBarcode {
    suspend fun find(code: String): BarcodeLookupResult
}

object BarcodePolicy {
    private val retailCode = Regex("^[0-9]{8,14}$")

    fun normalize(value: String): String = value.trim()

    fun validationMessage(value: String): String? {
        val code = normalize(value)
        return when {
            code.isEmpty() -> "Ingresa o escanea un código"
            code.length > 256 -> "El código es demasiado largo"
            code.all(Char::isDigit) && !retailCode.matches(code) -> "El código numérico debe tener entre 8 y 14 dígitos"
            else -> null
        }
    }
}
