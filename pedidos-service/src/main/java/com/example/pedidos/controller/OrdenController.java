package com.example.pedidos.controller;

import com.example.pedidos.model.Orden;
import com.example.pedidos.service.OrdenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/checkout")
    public Orden checkout(@RequestBody Map<String, Object> body) {
        Integer usuarioId = (Integer) body.get("usuarioId");
        Integer direccionId = (Integer) body.get("direccionId");
        Integer metodopagoId = (Integer) body.get("metodopagoId");
        return ordenService.generarOrdenDesdeCarrito(usuarioId, direccionId, metodopagoId);
    }
}