package org.example.controller;

import org.example.model.Proveedor;
import org.example.service.ProveedorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.dto.ProveedorResponseDTO;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Integer>> crear(@Valid @RequestBody Proveedor proveedor) {
        Integer proveedorId = proveedorService.crear(proveedor);
        return ResponseEntity.created(URI.create("/api/proveedores/" + proveedorId))
                .body(Map.of("proveedorId", proveedorId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable Integer id, @Valid @RequestBody Proveedor proveedor) {
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

    // Incluye inactivos: el panel de administracion necesita seguir listandolos
    // (marcados como "Inactivo"), y el frontend filtra por "activo" donde corresponda
    // (p.ej. al elegir proveedor para una nueva orden de compra).
    @GetMapping
    public List<ProveedorResponseDTO> listarTodos() {
        return proveedorService.listarTodos().stream()
                .map(ProveedorResponseDTO::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ProveedorResponseDTO obtenerPorId(@PathVariable Integer id) {
        return ProveedorResponseDTO.from(proveedorService.obtenerPorId(id));
    }
}
