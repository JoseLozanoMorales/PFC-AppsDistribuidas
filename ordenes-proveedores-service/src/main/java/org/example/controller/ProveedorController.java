package org.example.controller;

import org.example.model.Proveedor;
import org.example.service.ProveedorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Integer>> crear(@RequestBody Proveedor proveedor) {
        Integer proveedorId = proveedorService.crear(proveedor);
        return ResponseEntity.created(URI.create("/api/proveedores/" + proveedorId))
                .body(Map.of("proveedorId", proveedorId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable Integer id, @RequestBody Proveedor proveedor) {
        proveedorService.actualizar(id, proveedor);
        return ResponseEntity.noContent().build();
    }

    // Soft delete: sp_desactivar_proveedor solo pone activo = FALSE, no borra la fila
    // (las FK de orden_compra.proveedor_id la referencian).
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        proveedorService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<Proveedor> listarActivos() {
        return proveedorService.listarActivos();
    }

    @GetMapping("/{id}")
    public Proveedor obtenerPorId(@PathVariable Integer id) {
        return proveedorService.obtenerPorId(id);
    }
}
