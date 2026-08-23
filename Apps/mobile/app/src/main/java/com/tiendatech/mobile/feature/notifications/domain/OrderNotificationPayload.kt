package com.tiendatech.mobile.feature.notifications.domain

data class OrderNotificationPayload(
    val orderId: Long,
    val title: String,
    val message: String,
    val localDemo: Boolean = false
) {
    companion object {
        fun from(data: Map<String, String>): OrderNotificationPayload? {
            val orderId = data["ordenId"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return OrderNotificationPayload(
                orderId = orderId,
                title = data["titulo"].orEmpty().trim().takeIf(String::isNotEmpty) ?: "Actualización de pedido",
                message = data["mensaje"].orEmpty().trim().takeIf(String::isNotEmpty) ?: "Consulta los detalles de tu pedido.",
                localDemo = data["origen"] == "demo-local"
            )
        }
    }
}

fun interface OrderNotificationPublisher {
    fun publish(payload: OrderNotificationPayload): Boolean
}
