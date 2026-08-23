package com.example.inventario.domain;

import java.util.List;
import java.util.Map;

/** Puerto de salida para la persistencia del inventario. */
public interface InventarioRepository {
    List<Map<String, Object>> listarMovimientos();

    List<Map<String, Object>> listarSubtipos(Integer tipo);

    StockProducto obtenerStock(Integer productoId);

    List<StockProducto> listarStock(List<Integer> productoIds);

    boolean registrarMovimiento(String movimientoJson, String usuario, String idempotencyKey);
}
