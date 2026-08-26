package com.tiendatech.ordenesproveedores.domain;

import java.math.BigDecimal;
import java.util.Map;

/** Puerto de salida para registrar recepciones en inventario. */
public interface InventarioPort {
    // costoPorProducto: costo_unitario negociado en la orden por cada producto recibido,
    // necesario para que inventario-service recalcule el costo promedio ponderado (kardex)
    // con el costo real de compra en vez de conservar el anterior.
    void registrarEntradasPorRecepcion(Integer ordenCompraId,
                                       Map<Integer, Integer> recepcionPorProducto,
                                       Map<Integer, BigDecimal> costoPorProducto,
                                       String usuario);
}
