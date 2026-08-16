package org.example.repository;

import org.example.model.Factura;
import org.example.model.FacturaDetalle;

import java.util.List;

public interface FacturaStore {
    Integer generarDesdeOrden(Integer ordenId);
    Factura obtenerPorId(Integer facturaId);
    List<FacturaDetalle> listarDetalle(Integer facturaId);
    List<Factura> listar(Integer usuarioId);
}
