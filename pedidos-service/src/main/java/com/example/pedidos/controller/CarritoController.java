package com.example.pedidos.controller;

import com.example.pedidos.model.Carrito;
import com.example.pedidos.model.CarritoDetalle;
import com.example.pedidos.service.CarritoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/carrito")
public class CarritoController {

    private final CarritoService carritoService;

    @Autowired
    public CarritoController(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    @GetMapping("/{usuarioId}")
    public Carrito obtenerCarrito(@PathVariable Integer usuarioId) {
        Carrito carrito = carritoService.obtenerCarritoActivo(usuarioId);
        if (carrito == null) {
            carrito = carritoService.crearCarrito(usuarioId);
        }
        return carrito;
    }

    @GetMapping("/{carritoId}/detalle")
    public List<CarritoDetalle> obtenerDetalle(@PathVariable Integer carritoId) {
        return carritoService.listarDetalle(carritoId);
    }

    @PostMapping("/{carritoId}/agregar")
    public void agregarProducto(@PathVariable Integer carritoId, @RequestBody Map<String, Object> body) {
        Integer productoId = (Integer) body.get("productoId");
        Integer cantidad = (Integer) body.get("cantidad");
        carritoService.agregarProducto(carritoId, productoId, cantidad);
    }

    @DeleteMapping("/{carritoId}/quitar/{productoId}")
    public void quitarProducto(@PathVariable Integer carritoId, @PathVariable Integer productoId) {
        carritoService.quitarProducto(carritoId, productoId);
    }
}