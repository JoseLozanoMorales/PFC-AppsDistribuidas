package com.example.productos.domain;

/** Datos de una imagen recibidos por el caso de uso, sin tipos de Spring MVC. */
public record ImagenProducto(byte[] contenido, String mimeType, long pesoBytes) {
    public boolean estaVacia() {
        return contenido == null || contenido.length == 0;
    }
}
