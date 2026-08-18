package org.example.service;

import org.example.model.Factura;
import org.example.model.FacturaDetalle;
import org.example.repository.FacturaStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FacturaService {

    private final FacturaStore facturaRepository;
    private final ObjectProvider<InventarioFacturaDispatcher> inventarioDispatcher;

    public FacturaService(FacturaStore facturaRepository,
                          ObjectProvider<InventarioFacturaDispatcher> inventarioDispatcher) {
        this.facturaRepository = facturaRepository;
        this.inventarioDispatcher = inventarioDispatcher;
    }

    public Integer generarDesdeOrden(Integer ordenId) {
        Integer facturaId = facturaRepository.generarDesdeOrden(ordenId);
        InventarioFacturaDispatcher dispatcher = inventarioDispatcher.getIfAvailable();
        if (dispatcher != null) {
            dispatcher.despachar(facturaId, facturaRepository.listarDetalle(facturaId));
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
