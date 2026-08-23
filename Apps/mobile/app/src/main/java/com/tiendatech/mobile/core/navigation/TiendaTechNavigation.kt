package com.tiendatech.mobile.core.navigation

object TiendaTechDestinations {
    const val HOME_ROUTE = "home"
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
    const val RECOVERY_ROUTE = "recovery"
    const val PRODUCT_ROUTE = "product/{productId}"
    const val ACCOUNT_ROUTE = "account"
    const val CART_ROUTE = "cart"
    const val CHECKOUT_ROUTE = "checkout"
    const val ORDERS_ROUTE = "orders"
    const val ORDER_ROUTE = "order/{orderId}"
    const val SCANNER_ROUTE = "scanner"
    const val NOTIFICATIONS_ROUTE = "notifications"
    const val ORDER_DEEP_LINK = "tiendatech://orders/{orderId}"
    fun order(orderId: Long) = "order/$orderId"
    fun product(productId: Long) = "product/$productId"
}
