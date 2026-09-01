package com.tiendatech.productos.domain;

import java.util.List;
import java.util.Map;

/** Contrato HTTP con Ventas; evita consultar directamente su esquema. */
public interface VentasPort {
    List<Map<String, Object>> masVendidos(int limite);
}
