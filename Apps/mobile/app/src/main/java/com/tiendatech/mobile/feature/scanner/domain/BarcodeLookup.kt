package com.tiendatech.mobile.feature.scanner.domain

sealed interface BarcodeLookupResult {
    data class Found(val productId: Long, val productName: String) : BarcodeLookupResult
    data object NotFound : BarcodeLookupResult
    data object BackendUnavailable : BarcodeLookupResult
}

fun interface ProductLookupByBarcode {
    suspend fun find(code: String): BarcodeLookupResult
}

data class DemoBarcodeProduct(
    val code: String,
    val productId: Long,
    val productName: String,
    val categoryId: Long,
    val categoryName: String
)

object DemoBarcodeCatalog {
    val entries = listOf(
        DemoBarcodeProduct("7700000000019", 6, "Kingston NV3 1TB M.2 NVMe SSD", 1, "Almacenamiento"),
        DemoBarcodeProduct("7700000000026", 1, "ASUS Dual GeForce RTX 5060 8GB", 6, "Tarjeta gráfica"),
        DemoBarcodeProduct("7700000000033", 14, "CORSAIR VENGEANCE RGB DDR5 32GB", 7, "Memoria RAM"),
        DemoBarcodeProduct("7700000000040", 21, "ASUS ROG Maximus XI Hero Z390", 8, "Motherboard"),
        DemoBarcodeProduct("7700000000057", 5, "Samsung SSD 990 EVO Plus 4TB", 1, "Almacenamiento")
    )

    fun find(code: String): DemoBarcodeProduct? = entries.firstOrNull { it.code == code }
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
