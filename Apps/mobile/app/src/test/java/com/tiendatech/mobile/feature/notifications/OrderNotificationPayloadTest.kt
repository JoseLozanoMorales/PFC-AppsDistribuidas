package com.tiendatech.mobile.feature.notifications

import com.tiendatech.mobile.feature.notifications.domain.OrderNotificationPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderNotificationPayloadTest {
    @Test fun `rechaza payload sin orden valida`() {
        assertNull(OrderNotificationPayload.from(emptyMap()))
        assertNull(OrderNotificationPayload.from(mapOf("ordenId" to "0")))
    }

    @Test fun `mapea payload remoto sin depender de Firebase`() {
        val payload = OrderNotificationPayload.from(mapOf("ordenId" to "91", "titulo" to "Pedido", "mensaje" to "Actualizado"))!!
        assertEquals(91L, payload.orderId); assertEquals("Pedido", payload.title); assertEquals("Actualizado", payload.message); assertFalse(payload.localDemo)
    }

    @Test fun `aplica textos seguros y reconoce demo local`() {
        val payload = OrderNotificationPayload.from(mapOf("ordenId" to "7", "origen" to "demo-local"))!!
        assertEquals("Actualización de pedido", payload.title); assertTrue(payload.localDemo)
    }
}
