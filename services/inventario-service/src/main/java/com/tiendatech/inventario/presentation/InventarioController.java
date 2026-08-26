package com.tiendatech.inventario.presentation;

import com.tiendatech.inventario.application.InventarioService;
import com.tiendatech.inventario.domain.StockProducto;
import com.tiendatech.inventario.presentation.dto.StockProductoResponse;
import com.fasterxml.jackson.databind.JsonNode;
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
    public StockProductoResponse obtenerStock(@PathVariable Integer productoId) {
        return toResponse(service.obtenerStock(productoId));
    }

    @PostMapping("/api/stock")
    public List<StockProductoResponse> listarStock(@RequestBody List<Integer> productoIds) {
        return service.listarStock(productoIds).stream().map(InventarioController::toResponse).toList();
    }

    @PostMapping("/api/sp/movimiento-inventario")
    public ResponseEntity<Void> registrarMovimiento(
            @RequestBody JsonNode body,
            @RequestParam(value = "usuario", required = false) String usuarioParam,
            @RequestHeader(value = "X-Usuario", required = false) String usuarioHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String usuario = usuarioParam != null && !usuarioParam.isBlank() ? usuarioParam : usuarioHeader;
        boolean replayed = service.registrarMovimiento(body, usuario, idempotencyKey);
        return ResponseEntity.noContent()
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .build();
    }

    private static StockProductoResponse toResponse(StockProducto stock) {
        return new StockProductoResponse(stock.productoId(), stock.nombre(), stock.stock());
    }
}
