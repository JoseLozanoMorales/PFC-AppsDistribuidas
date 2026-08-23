package org.example.presentation;

import org.example.application.OrdenService;
import org.example.domain.Orden;
import org.example.domain.PageResponse;
import org.example.domain.Paginacion;
import org.example.infrastructure.config.AuthUsuario;
import org.example.infrastructure.config.AuthenticatedUser;
import org.example.presentation.dto.DetalleOrdenResponse;
import org.example.presentation.dto.OrdenResponse;
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
    public PageResponse<OrdenResponse> listarOrdenes(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @AuthUsuario AuthenticatedUser usuario) {
        if (!usuario.esAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requiere rol administrador");
        }
        return mapOrdenes(ordenService.listarOrdenes(Paginacion.de(page, size)));
    }

    @GetMapping("/{ordenId}")
    public OrdenResponse obtenerOrden(@PathVariable Integer ordenId, @AuthUsuario AuthenticatedUser usuario) {
        Orden orden = ordenService.obtenerOrdenPorId(ordenId);
        if (orden == null || !orden.getUsuarioId().equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden " + ordenId + " no encontrada");
        }
        return OrdenResponse.from(orden);
    }

    @GetMapping("/{ordenId}/detalle")
    public PageResponse<DetalleOrdenResponse> obtenerDetalleOrden(@PathVariable Integer ordenId,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            @AuthUsuario AuthenticatedUser usuario) {
        Orden orden = ordenService.obtenerOrdenPorId(ordenId);
        if (orden == null || !orden.getUsuarioId().equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden " + ordenId + " no encontrada");
        }
        var pagina = ordenService.obtenerDetalleOrden(ordenId, orden.getFecha(), Paginacion.de(page, size));
        return new PageResponse<>(
                pagina.content().stream().map(DetalleOrdenResponse::from).toList(),
                pagina.page(), pagina.size(), pagina.totalElements(), pagina.totalPages());
    }

    @GetMapping("/usuario/{usuarioId}")
    public PageResponse<OrdenResponse> obtenerOrdenesPorUsuario(@PathVariable Integer usuarioId,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size,
                                                          @AuthUsuario AuthenticatedUser usuario) {
        if (!usuarioId.equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
        }
        return mapOrdenes(ordenService.listarOrdenesPorUsuario(usuarioId, Paginacion.de(page, size)));
    }

    // Idempotency-Key es opcional: sin ella el checkout se comporta exactamente
    // igual que antes (no rompe al frontend). Con ella, un reintento con la misma
    // clave devuelve la orden ya creada en vez de duplicarla (ver
    // docs/idempotencia.sql -- requiere pedidos.idempotencia.enabled=true).
    @PostMapping("/checkout")
    public ResponseEntity<OrdenResponse> checkout(@RequestBody Map<String, Object> body,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                           @AuthUsuario AuthenticatedUser usuario) {
        Integer direccionId = (Integer) body.get("direccionId");
        Integer metodopagoId = (Integer) body.get("metodopagoId");
        Orden orden = ordenService.generarOrdenDesdeCarrito(usuario.userId(), direccionId, metodopagoId, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/ordenes/{id}")
                .buildAndExpand(orden.getOrdenId())
                .toUri();
        return ResponseEntity.created(location).body(OrdenResponse.from(orden));
    }

    private static PageResponse<OrdenResponse> mapOrdenes(PageResponse<Orden> pagina) {
        return new PageResponse<>(
                pagina.content().stream().map(OrdenResponse::from).toList(),
                pagina.page(), pagina.size(), pagina.totalElements(), pagina.totalPages());
    }
}
