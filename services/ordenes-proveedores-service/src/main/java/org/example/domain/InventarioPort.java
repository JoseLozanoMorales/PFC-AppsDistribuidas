package org.example.domain;

import java.util.Map;

/** Puerto de salida para registrar recepciones en inventario. */
public interface InventarioPort {
    void registrarEntradasPorRecepcion(Integer ordenCompraId,
                                       Map<Integer, Integer> recepcionPorProducto,
                                       String usuario);
}
