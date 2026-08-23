package com.example.inventario.domain;

/** Entidad de dominio libre de anotaciones de Spring, JDBC o serializacion. */
public record StockProducto(Long productoId, String nombre, Integer stock) {
}
