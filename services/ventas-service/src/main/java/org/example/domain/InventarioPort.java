package org.example.domain;

import java.util.List;

/** Puerto de salida hacia inventario-service. */
public interface InventarioPort {
    void registrarSalidasPorFactura(Integer facturaId, List<FacturaDetalle> detalle, String usuario);
}
