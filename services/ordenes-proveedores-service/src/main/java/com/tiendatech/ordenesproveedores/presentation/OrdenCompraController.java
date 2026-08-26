package com.tiendatech.ordenesproveedores.presentation;

import com.tiendatech.ordenesproveedores.presentation.dto.ActualizarOrdenCompraRequest;
import com.tiendatech.ordenesproveedores.presentation.dto.CrearOrdenCompraRequest;
import com.tiendatech.ordenesproveedores.domain.EstadoOrdenCompra;
import com.tiendatech.ordenesproveedores.application.OrdenCompraService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.tiendatech.ordenesproveedores.presentation.dto.OrdenCompraResponseDTO;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public List<OrdenCompraResponseDTO> listarPorEstado(@RequestParam(required = false) EstadoOrdenCompra estado) {
        return ordenCompraService.listarPorEstado(estado).stream()
                .map(OrdenCompraResponseDTO::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public OrdenCompraResponseDTO obtenerPorId(@PathVariable Integer id) {
        return OrdenCompraResponseDTO.from(ordenCompraService.obtenerPorId(id));
    }

    @GetMapping("/{id}/detalle")
    public List<com.tiendatech.ordenesproveedores.presentation.dto.DetalleOrdenCompraResponseDTO> listarDetalle(@PathVariable Integer id) {
        return ordenCompraService.listarDetalle(id).stream()
                .map(com.tiendatech.ordenesproveedores.presentation.dto.DetalleOrdenCompraResponseDTO::from)
                .collect(Collectors.toList());
    }
}
