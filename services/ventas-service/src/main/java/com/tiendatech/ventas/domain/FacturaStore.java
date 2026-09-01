package com.tiendatech.ventas.domain;

import java.util.List;
import java.util.Map;

/**
 * Puerto de dominio (patron Repository) para la persistencia de facturas.
 * La implementacion real vive en infrastructure.persistence.CrdbFacturaRepository.
 */
public interface FacturaStore {
    Integer generar(FacturaDraft draft);
    Factura obtenerPorId(Integer facturaId);
    List<FacturaDetalle> listarDetalle(Integer facturaId);
    List<Factura> listar(Integer usuarioId);
    List<Map<String, Object>> masVendidos(int limite);
}
