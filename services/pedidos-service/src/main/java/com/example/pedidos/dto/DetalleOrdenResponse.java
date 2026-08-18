package com.example.pedidos.dto;

import com.example.pedidos.model.DetalleOrden;

import java.math.BigDecimal;

public record DetalleOrdenResponse(
        Integer ordenId,
        Integer productoId,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total) {

    public static DetalleOrdenResponse from(DetalleOrden detalle) {
        return new DetalleOrdenResponse(
                detalle.getOrdenId(),
                detalle.getProductoId(),
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getSubtotal(),
                detalle.getIva(),
                detalle.getTotal());
    }
}
