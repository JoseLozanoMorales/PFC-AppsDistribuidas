package com.tiendatech.inventario.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StockProductoResponse(
        @JsonProperty("producto_id") Long productoId,
        String nombre,
        Integer stock) {
}
