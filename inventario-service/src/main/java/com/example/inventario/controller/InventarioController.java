package com.example.inventario.controller;

import com.example.inventario.service.InventarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class InventarioController {
    private final InventarioService service;

    public InventarioController(InventarioService service) {
        this.service = service;
    }

    @GetMapping("/api/movimientos")
    public List<Map<String, Object>> listarMovimientos() {
        return service.listarMovimientos();
    }

    @GetMapping("/api/subtipos-movimiento")
    public List<Map<String, Object>> listarSubtipos(
            @RequestParam(name = "tipo", required = false) Integer tipo) {
        return service.listarSubtipos(tipo);
    }

    @GetMapping("/api/stock/{productoId}")
    public Map<String, Object> obtenerStock(@PathVariable Integer productoId) {
        return service.obtenerStock(productoId);
    }

    @PostMapping("/api/stock")
    public List<Map<String, Object>> listarStock(@RequestBody List<Integer> productoIds) {
        return service.listarStock(productoIds);
    }

    @PostMapping("/api/sp/movimiento-inventario")
    public ResponseEntity<Void> registrarMovimiento(
            @RequestBody Object body,
            @RequestParam(value = "usuario", required = false) String usuarioParam,
            @RequestHeader(value = "X-Usuario", required = false) String usuarioHeader) {
        String usuario = usuarioParam != null && !usuarioParam.isBlank() ? usuarioParam : usuarioHeader;
        service.registrarMovimiento(body, usuario);
        return ResponseEntity.noContent().build();
    }
}
