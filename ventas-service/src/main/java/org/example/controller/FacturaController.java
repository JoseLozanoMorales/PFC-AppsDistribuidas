package org.example.controller;

import org.example.model.Factura;
import org.example.model.FacturaDetalle;
import org.example.service.FacturaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/facturas")
public class FacturaController {

    private final FacturaService facturaService;

    public FacturaController(FacturaService facturaService) {
        this.facturaService = facturaService;
    }

    // Body: { "ordenId": 5 }
    @PostMapping
    public ResponseEntity<Map<String, Integer>> generarDesdeOrden(@RequestBody Map<String, Integer> body) {
        Integer ordenId = body.get("ordenId");
        if (ordenId == null) {
            throw new IllegalArgumentException("Falta ordenId en el body");
        }
        Integer facturaId = facturaService.generarDesdeOrden(ordenId);
        return ResponseEntity.created(URI.create("/api/facturas/" + facturaId))
                .body(Map.of("facturaId", facturaId));
    }

    @GetMapping
    public List<Factura> listar(@RequestParam(required = false) Integer usuarioId) {
        return facturaService.listar(usuarioId);
    }

    @GetMapping("/{id}")
    public Factura obtenerPorId(@PathVariable Integer id) {
        return facturaService.obtenerPorId(id);
    }

    @GetMapping("/{id}/detalle")
    public List<FacturaDetalle> listarDetalle(@PathVariable Integer id) {
        return facturaService.listarDetalle(id);
    }
}