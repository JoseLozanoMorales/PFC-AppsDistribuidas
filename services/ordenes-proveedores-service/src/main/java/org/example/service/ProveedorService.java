package org.example.service;

import org.example.model.Proveedor;
import org.example.repository.ProveedorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    public ProveedorService(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    public Integer crear(Proveedor proveedor) {
        return proveedorRepository.crear(proveedor);
    }

    public void actualizar(Integer proveedorId, Proveedor proveedor) {
        proveedor.setProveedorId(proveedorId);
        proveedorRepository.actualizar(proveedor);
    }

    public void desactivar(Integer proveedorId) {
        proveedorRepository.desactivar(proveedorId);
    }

    public void activar(Integer proveedorId) {
        proveedorRepository.activar(proveedorId);
    }

    public List<Proveedor> listarTodos() {
        return proveedorRepository.listarTodos();
    }

    public Proveedor obtenerPorId(Integer proveedorId) {
        Proveedor proveedor = proveedorRepository.obtenerPorId(proveedorId);
        if (proveedor == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor " + proveedorId + " no existe");
        }
        return proveedor;
    }
}