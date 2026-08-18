package com.example.pedidos.controller;

import com.example.pedidos.model.Carrito;
import com.example.pedidos.model.CarritoDetalle;
import com.example.pedidos.security.AuthUsuario;
import com.example.pedidos.security.AuthenticatedUser;
import com.example.pedidos.service.CarritoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public Carrito obtenerCarrito(@PathVariable Integer usuarioId, @AuthUsuario AuthenticatedUser usuario) {
        verificarPropioUsuario(usuarioId, usuario);
        Carrito carrito = carritoService.obtenerCarritoActivo(usuarioId);
        if (carrito == null) {
            carrito = carritoService.crearCarrito(usuarioId);
        }
        return carrito;
    }

    @GetMapping("/{carritoId}/detalle")
    public List<CarritoDetalle> obtenerDetalle(@PathVariable Integer carritoId, @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        return carritoService.listarDetalle(carritoId);
    }

    @PostMapping("/{carritoId}/agregar")
    public void agregarProducto(@PathVariable Integer carritoId, @RequestBody Map<String, Object> body,
                                 @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        Integer productoId = (Integer) body.get("productoId");
        Integer cantidad = (Integer) body.get("cantidad");
        carritoService.agregarProducto(carritoId, productoId, cantidad);
    }

    @DeleteMapping("/{carritoId}/quitar/{productoId}")
    public void quitarProducto(@PathVariable Integer carritoId, @PathVariable Integer productoId,
                                @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        carritoService.quitarProducto(carritoId, productoId);
    }

    @PutMapping("/{carritoId}/actualizar/{productoId}")
    public void actualizarCantidad(@PathVariable Integer carritoId, @PathVariable Integer productoId,
                                   @RequestBody Map<String, Object> body, @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        Integer cantidad = (Integer) body.get("cantidad");
        carritoService.actualizarCantidad(carritoId, productoId, cantidad);
    }

    private void verificarPropioUsuario(Integer usuarioId, AuthenticatedUser usuario) {
        if (!usuarioId.equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Carrito no encontrado");
        }
    }

    private void verificarPropietarioDeCarrito(Integer carritoId, AuthenticatedUser usuario) {
        Carrito carrito = carritoService.obtenerCarritoPorId(carritoId);
        if (carrito == null || !carrito.getUsuarioId().equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Carrito no encontrado");
        }
    }
}
