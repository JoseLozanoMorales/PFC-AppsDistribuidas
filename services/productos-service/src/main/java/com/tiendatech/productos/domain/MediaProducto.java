package com.tiendatech.productos.domain;

/** Contenido multimedia perteneciente a un producto. */
public record MediaProducto(byte[] bytes, String mimeType, Long length) {
}
