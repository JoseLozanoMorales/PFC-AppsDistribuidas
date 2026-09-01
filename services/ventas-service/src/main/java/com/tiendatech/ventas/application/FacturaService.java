package com.tiendatech.ventas.application;

import com.tiendatech.ventas.domain.Factura;
import com.tiendatech.ventas.domain.FacturaDetalle;
import com.tiendatech.ventas.domain.FacturaStore;
import com.tiendatech.ventas.domain.FacturaDraft;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class FacturaService {

    private final FacturaStore facturaRepository;

    public FacturaService(FacturaStore facturaRepository) {
        this.facturaRepository = facturaRepository;
    }

    public Integer generar(FacturaDraft draft) {
        return facturaRepository.generar(draft);
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

    public List<Map<String, Object>> masVendidos(int limite) {
        return facturaRepository.masVendidos(Math.min(Math.max(limite, 1), 100));
    }
}
