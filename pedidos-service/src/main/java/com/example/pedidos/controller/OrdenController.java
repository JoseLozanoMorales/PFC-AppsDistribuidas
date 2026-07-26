package com.example.pedidos.controller;

import com.example.pedidos.model.DetalleOrden;
import com.example.pedidos.model.Orden;
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

    @GetMapping
    public List<Orden> listarOrdenes() {
        return ordenService.listarOrdenes();
    }

    @GetMapping("/{ordenId}")
    public Orden obtenerOrden(@PathVariable Integer ordenId) {
        Orden orden = ordenService.obtenerOrdenPorId(ordenId);
        if (orden == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden " + ordenId + " no encontrada");
        }
        return orden;
    }

    @GetMapping("/{ordenId}/detalle")
    public List<DetalleOrden> obtenerDetalleOrden(@PathVariable Integer ordenId) {
        return ordenService.obtenerDetalleOrden(ordenId);
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<Orden> obtenerOrdenesPorUsuario(@PathVariable Integer usuarioId) {
        return ordenService.listarOrdenesPorUsuario(usuarioId);
    }

    @PostMapping("/checkout")
    public Orden checkout(@RequestBody Map<String, Object> body) {
        Integer usuarioId = (Integer) body.get("usuarioId");
        Integer direccionId = (Integer) body.get("direccionId");
        Integer metodopagoId = (Integer) body.get("metodopagoId");
        return ordenService.generarOrdenDesdeCarrito(usuarioId, direccionId, metodopagoId);
    }
}