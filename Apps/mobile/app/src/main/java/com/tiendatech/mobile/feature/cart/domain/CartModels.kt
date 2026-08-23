package com.tiendatech.mobile.feature.cart.domain

import com.tiendatech.mobile.feature.catalog.domain.Product

data class CartLine(
    val productId: Long,
    val quantity: Int,
    val unitPrice: Double,
    val product: Product?
) {
    val subtotal: Double get() = unitPrice * quantity
}

data class ShoppingCart(
    val id: Long,
    val lines: List<CartLine>
) {
    val units: Int get() = lines.sumOf(CartLine::quantity)
    val total: Double get() = lines.sumOf(CartLine::subtotal)
}

sealed interface CartResult<out T> {
    data class Success<T>(val value: T) : CartResult<T>
    data class Failure(val message: String) : CartResult<Nothing>
    data object Unauthorized : CartResult<Nothing>
}
