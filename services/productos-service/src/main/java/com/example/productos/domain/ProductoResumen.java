package com.example.productos.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Entidad de catalogo libre de anotaciones de framework. */
public record ProductoResumen(
        Long productoId,
        String nombre,
        BigDecimal precioUnitario,
        String enlace,
        LocalDate fecha,
        Integer stock,
        Long marcaId,
        Long gamaId,
        Long ivaId,
        BigDecimal costo,
        Boolean habilitado,
        Long galeriaId) {
}
