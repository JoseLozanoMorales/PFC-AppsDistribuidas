package org.example.presentation;

import org.example.application.CarritoService;
import org.example.domain.Carrito;
import org.example.domain.CarritoDetalle;
import org.example.domain.PageResponse;
import org.example.domain.Paginacion;
import org.example.infrastructure.config.AuthUsuario;
import org.example.infrastructure.config.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public PageResponse<CarritoDetalle> obtenerDetalle(@PathVariable Integer carritoId,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size,
                                                         @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        return carritoService.listarDetalle(carritoId, Paginacion.de(page, size));
    }

    @PostMapping("/{carritoId}/agregar")
    public ResponseEntity<Void> agregarProducto(@PathVariable Integer carritoId, @RequestBody Map<String, Object> body,
                                                 @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        Integer productoId = (Integer) body.get("productoId");
        Integer cantidad = (Integer) body.get("cantidad");
        carritoService.agregarProducto(carritoId, productoId, cantidad);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{carritoId}/quitar/{productoId}")
    public ResponseEntity<Void> quitarProducto(@PathVariable Integer carritoId, @PathVariable Integer productoId,
                                                @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        carritoService.quitarProducto(carritoId, productoId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{carritoId}/actualizar/{productoId}")
    public ResponseEntity<Void> actualizarCantidad(@PathVariable Integer carritoId, @PathVariable Integer productoId,
                                                    @RequestBody Map<String, Object> body,
                                                    @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        Integer cantidad = (Integer) body.get("cantidad");
        carritoService.actualizarCantidad(carritoId, productoId, cantidad);
        return ResponseEntity.noContent().build();
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
