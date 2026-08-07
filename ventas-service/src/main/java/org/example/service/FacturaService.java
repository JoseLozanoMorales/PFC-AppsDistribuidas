package org.example.service;

import org.example.model.Factura;
import org.example.model.FacturaDetalle;
import org.example.repository.FacturaStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FacturaService {

    private final FacturaStore facturaRepository;
    private final org.example.client.InventarioClient inventarioClient;

    public FacturaService(FacturaStore facturaRepository,
                          org.example.client.InventarioClient inventarioClient) {
        this.facturaRepository = facturaRepository;
        this.inventarioClient = inventarioClient;
    }

    public Integer generarDesdeOrden(Integer ordenId) {
        Integer facturaId = facturaRepository.generarDesdeOrden(ordenId);

        List<FacturaDetalle> detalle = facturaRepository.listarDetalle(facturaId);
        try {
            inventarioClient.registrarSalidasPorFactura(facturaId, detalle, null);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "La factura " + facturaId + " se generó, pero falló el descuento de stock "
                            + "en inventario-service. Revisar manualmente.", e);
        }

        return facturaId;
    }

    public Factura obtenerPorId(Integer facturaId) {
        Factura factura = facturaRepository.obtenerPorId(facturaId);
        if (factura == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La factura " + facturaId + " no existe");
        }
        return factura;
    }

    public List<FacturaDetalle> listarDetalle(Integer facturaId) {
        obtenerPorId(facturaId); // valida que exista -> 404 si no
        return facturaRepository.listarDetalle(facturaId);
    }

    public List<Factura> listar(Integer usuarioId) {
        return facturaRepository.listar(usuarioId);
    }
}
