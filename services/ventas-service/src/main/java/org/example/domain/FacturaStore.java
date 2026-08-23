package org.example.domain;

import java.util.List;

/**
 * Puerto de dominio (patron Repository) para la persistencia de facturas.
 * La implementacion real vive en infrastructure.persistence.CrdbFacturaRepository.
 */
public interface FacturaStore {
    Integer generarDesdeOrden(Integer ordenId);
    Factura obtenerPorId(Integer facturaId);
    List<FacturaDetalle> listarDetalle(Integer facturaId);
    List<Factura> listar(Integer usuarioId);
}
