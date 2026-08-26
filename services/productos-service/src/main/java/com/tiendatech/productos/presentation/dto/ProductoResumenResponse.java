package com.tiendatech.productos.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductoResumenResponse(
        @JsonProperty("producto_id") Long productoId,
        String nombre,
        BigDecimal preciounitario,
        String enlace,
        LocalDate fecha,
        Integer stock,
        @JsonProperty("marca_id") Long marcaId,
        @JsonProperty("gama_id") Long gamaId,
        @JsonProperty("iva_id") Long ivaId,
        BigDecimal costo,
        Boolean habilitado,
        @JsonProperty("galeria_id") Long galeriaId) {
}
