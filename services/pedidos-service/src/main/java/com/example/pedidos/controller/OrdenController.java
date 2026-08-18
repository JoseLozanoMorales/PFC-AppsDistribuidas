package com.example.pedidos.controller;

import com.example.pedidos.model.DetalleOrden;
import com.example.pedidos.model.Orden;
import com.example.pedidos.paging.PageResponse;
import com.example.pedidos.paging.Paginacion;
import com.example.pedidos.security.AuthUsuario;
import com.example.pedidos.security.AuthenticatedUser;
import com.example.pedidos.service.OrdenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/ordenes")
public class OrdenController {

    private final OrdenService ordenService;

    @Autowired
    public OrdenController(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    // Lista TODAS las ordenes de TODOS los usuarios: no es un endpoint de "orden
    // propia", requiere rol administrador.
    @GetMapping
    public PageResponse<Orden> listarOrdenes(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @AuthUsuario AuthenticatedUser usuario) {
        if (!usuario.esAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requiere rol administrador");
        }
        return ordenService.listarOrdenes(Paginacion.de(page, size));
    }

    @GetMapping("/{ordenId}")
    public Orden obtenerOrden(@PathVariable Integer ordenId, @AuthUsuario AuthenticatedUser usuario) {
        Orden orden = ordenService.obtenerOrdenPorId(ordenId);
        if (orden == null || !orden.getUsuarioId().equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden " + ordenId + " no encontrada");
        }
        return orden;
    }

    @GetMapping("/{ordenId}/detalle")
    public PageResponse<DetalleOrden> obtenerDetalleOrden(@PathVariable Integer ordenId,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            @AuthUsuario AuthenticatedUser usuario) {
        Orden orden = ordenService.obtenerOrdenPorId(ordenId);
        if (orden == null || !orden.getUsuarioId().equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden " + ordenId + " no encontrada");
        }
        return ordenService.obtenerDetalleOrden(ordenId, orden.getFecha(), Paginacion.de(page, size));
    }

    @GetMapping("/usuario/{usuarioId}")
    public PageResponse<Orden> obtenerOrdenesPorUsuario(@PathVariable Integer usuarioId,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size,
                                                          @AuthUsuario AuthenticatedUser usuario) {
        if (!usuarioId.equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
        }
        return ordenService.listarOrdenesPorUsuario(usuarioId, Paginacion.de(page, size));
    }

    // Idempotency-Key es opcional: sin ella el checkout se comporta exactamente
    // igual que antes (no rompe al frontend). Con ella, un reintento con la misma
    // clave devuelve la orden ya creada en vez de duplicarla (ver
    // docs/idempotencia.sql -- requiere pedidos.idempotencia.enabled=true).
    @PostMapping("/checkout")
    public ResponseEntity<Orden> checkout(@RequestBody Map<String, Object> body,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                           @AuthUsuario AuthenticatedUser usuario) {
        Integer direccionId = (Integer) body.get("direccionId");
        Integer metodopagoId = (Integer) body.get("metodopagoId");
        Orden orden = ordenService.generarOrdenDesdeCarrito(usuario.userId(), direccionId, metodopagoId, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/ordenes/{id}")
                .buildAndExpand(orden.getOrdenId())
                .toUri();
        return ResponseEntity.created(location).body(orden);
    }
}
