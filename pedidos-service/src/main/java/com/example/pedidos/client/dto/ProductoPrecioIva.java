package com.example.pedidos.client.dto;

import java.math.BigDecimal;

public record ProductoPrecioIva(
        Integer productoId,
        BigDecimal precioUnitario,
        Integer ivaId,
        BigDecimal porcentajeIva
) {
}
