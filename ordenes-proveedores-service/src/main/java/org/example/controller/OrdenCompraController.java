package org.example.controller;

import org.example.dto.ActualizarOrdenCompraRequest;
import org.example.dto.CrearOrdenCompraRequest;
import org.example.model.EstadoOrdenCompra;
import org.example.model.OrdenCompra;
import org.example.service.OrdenCompraService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ordenes-compra")
public class OrdenCompraController {

    private final OrdenCompraService ordenCompraService;

    public OrdenCompraController(OrdenCompraService ordenCompraService) {
        this.ordenCompraService = ordenCompraService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Integer>> crear(
            @RequestBody CrearOrdenCompraRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Integer usuarioId) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("Falta el header X-Usuario-Id");
        }
        Integer ordenCompraId = ordenCompraService.crear(
                request.proveedorId(), usuarioId, request.fechaEsperada(), request.detalle());

        return ResponseEntity.created(URI.create("/api/ordenes-compra/" + ordenCompraId))
                .body(Map.of("ordenCompraId", ordenCompraId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable Integer id,
                                           @RequestBody ActualizarOrdenCompraRequest request) {
        ordenCompraService.actualizar(id, request.proveedorId(), request.fechaEsperada(), request.detalle());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enviar")
    public ResponseEntity<Void> enviar(@PathVariable Integer id) {
        ordenCompraService.enviar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable Integer id) {
        ordenCompraService.cancelar(id);
        return ResponseEntity.noContent().build();
    }

    // Body: { "producto_id": cantidad, ... } -- cantidad que llega AHORA, se acumula
    // sobre cantidad_recibida (ver sp_registrar_recepcion_json).
    @PostMapping("/{id}/recepcion")
    public ResponseEntity<Void> registrarRecepcion(@PathVariable Integer id,
                                                   @RequestBody Map<Integer, Integer> recepcionPorProducto,
                                                   @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        ordenCompraService.registrarRecepcion(id, recepcionPorProducto, usuario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<OrdenCompra> listarPorEstado(@RequestParam(required = false) EstadoOrdenCompra estado) {
        return ordenCompraService.listarPorEstado(estado);
    }

    @GetMapping("/{id}")
    public OrdenCompra obtenerPorId(@PathVariable Integer id) {
        return ordenCompraService.obtenerPorId(id);
    }
}
