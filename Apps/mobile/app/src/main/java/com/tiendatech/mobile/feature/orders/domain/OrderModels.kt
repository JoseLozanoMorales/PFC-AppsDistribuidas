package com.tiendatech.mobile.feature.orders.domain

data class OrderSummary(val id: Long, val date: String, val subtotal: Double, val total: Double, val invoiceId: Long?, val invoiceNumber: String?)
data class OrderLine(val productId: Long, val productName: String?, val quantity: Int, val unitPrice: Double, val subtotal: Double, val tax: Double, val total: Double)
data class Invoice(val id: Long, val number: String, val issueDate: String, val customerName: String, val deliveryAddress: String, val subtotal: Double, val total: Double, val lines: List<OrderLine>)
data class OrderDetail(val summary: OrderSummary, val addressId: Long, val paymentMethodId: Long, val lines: List<OrderLine>, val invoice: Invoice?)
data class OrdersPage(val orders: List<OrderSummary>, val page: Int, val totalPages: Int)
sealed interface OrdersResult<out T> { data class Success<T>(val value: T) : OrdersResult<T>; data class Failure(val message: String) : OrdersResult<Nothing>; data object Unauthorized : OrdersResult<Nothing> }
