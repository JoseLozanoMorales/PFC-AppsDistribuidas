package com.example.pedidos.controller;

import com.example.pedidos.model.DetalleOrden;
import com.example.pedidos.model.Orden;
import com.example.pedidos.security.AuthUsuario;
import com.example.pedidos.security.AuthenticatedUser;
import com.example.pedidos.service.OrdenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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
    public List<Orden> listarOrdenes(@AuthUsuario AuthenticatedUser usuario) {
        if (!usuario.esAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requiere rol administrador");
        }
        return ordenService.listarOrdenes();
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
    public List<DetalleOrden> obtenerDetalleOrden(@PathVariable Integer ordenId, @AuthUsuario AuthenticatedUser usuario) {
        Orden orden = ordenService.obtenerOrdenPorId(ordenId);
        if (orden == null || !orden.getUsuarioId().equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden " + ordenId + " no encontrada");
        }
        return ordenService.obtenerDetalleOrden(ordenId);
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<Orden> obtenerOrdenesPorUsuario(@PathVariable Integer usuarioId, @AuthUsuario AuthenticatedUser usuario) {
        if (!usuarioId.equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
        }
        return ordenService.listarOrdenesPorUsuario(usuarioId);
    }

    @PostMapping("/checkout")
    public Orden checkout(@RequestBody Map<String, Object> body, @AuthUsuario AuthenticatedUser usuario) {
        Integer direccionId = (Integer) body.get("direccionId");
        Integer metodopagoId = (Integer) body.get("metodopagoId");
        return ordenService.generarOrdenDesdeCarrito(usuario.userId(), direccionId, metodopagoId);
    }
}
