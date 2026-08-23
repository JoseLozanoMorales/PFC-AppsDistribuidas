package com.tiendatech.mobile.feature.catalog.domain

data class Product(
    val id: Long,
    val name: String,
    val description: String?,
    val price: Double,
    val stock: Int?,
    val categoryId: Long?,
    val categoryName: String?,
    val imageId: Long?,
    val galleryImageIds: List<Long> = emptyList()
) {
    val available: Boolean get() = (stock ?: 0) > 0
}

data class Category(val id: Long, val name: String)

data class CatalogSnapshot(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList()
)

sealed interface CatalogResult<out T> {
    data class Success<T>(val value: T) : CatalogResult<T>
    data class Failure(val message: String) : CatalogResult<Nothing>
}
