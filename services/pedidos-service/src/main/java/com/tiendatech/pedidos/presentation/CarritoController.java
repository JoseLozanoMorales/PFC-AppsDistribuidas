package com.tiendatech.pedidos.presentation;

import com.tiendatech.pedidos.application.CarritoService;
import com.tiendatech.pedidos.domain.Carrito;
import com.tiendatech.pedidos.domain.CarritoDetalle;
import com.tiendatech.pedidos.domain.PageResponse;
import com.tiendatech.pedidos.domain.Paginacion;
import com.tiendatech.pedidos.infrastructure.config.AuthUsuario;
import com.tiendatech.pedidos.infrastructure.config.AuthenticatedUser;
import com.tiendatech.pedidos.infrastructure.observability.TraceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

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

    // X-Trace-Id: mismo patron que OrdenController#checkout -- se acepta el
    // header entrante o se genera uno nuevo, se publica en TraceContext (leido
    // por LengthPrefixedTcpReservationClient para viajar dentro del sobre TCP
    // hacia inventario-service) y se devuelve en la respuesta para que el
    // llamador pueda correlacionar. Antes, ninguno de estos tres endpoints
    // -- los unicos que disparan el canal TCP de reservas -- generaba o
    // propagaba un X-Trace-Id.
    @PostMapping("/{carritoId}/agregar")
    public ResponseEntity<?> agregarProducto(@PathVariable Integer carritoId, @RequestBody Map<String, Object> body,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String incomingTraceId,
                                                 @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        String traceId = traceId(incomingTraceId);
        TraceContext.set(traceId, "none");
        try {
            Integer productoId = (Integer) body.get("productoId");
            Integer cantidad = (Integer) body.get("cantidad");
            var result = carritoService.agregarProducto(carritoId, usuario.userId(), productoId, cantidad,
                    text(body, "deviceId", "legacy-web"), number(body, "lamportTimestamp", 0),
                    text(body, "operationId", UUID.randomUUID().toString()));
            HttpStatus status = result.accepted() ? HttpStatus.OK : HttpStatus.CONFLICT;
            return ResponseEntity.status(status).header("X-Trace-Id", traceId).body(result);
        } finally {
            TraceContext.clear();
        }
    }

    @DeleteMapping("/{carritoId}/quitar/{productoId}")
    public ResponseEntity<Void> quitarProducto(@PathVariable Integer carritoId, @PathVariable Integer productoId,
                                                @RequestHeader(value = "X-Trace-Id", required = false) String incomingTraceId,
                                                @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        String traceId = traceId(incomingTraceId);
        TraceContext.set(traceId, "none");
        try {
            carritoService.actualizarCantidad(carritoId, usuario.userId(), productoId, 0,
                    "legacy-web", 0, UUID.randomUUID().toString());
            return ResponseEntity.noContent().header("X-Trace-Id", traceId).build();
        } finally {
            TraceContext.clear();
        }
    }

    @PutMapping("/{carritoId}/actualizar/{productoId}")
    public ResponseEntity<?> actualizarCantidad(@PathVariable Integer carritoId, @PathVariable Integer productoId,
                                                    @RequestBody Map<String, Object> body,
                                                    @RequestHeader(value = "X-Trace-Id", required = false) String incomingTraceId,
                                                    @AuthUsuario AuthenticatedUser usuario) {
        verificarPropietarioDeCarrito(carritoId, usuario);
        String traceId = traceId(incomingTraceId);
        TraceContext.set(traceId, "none");
        try {
            Integer cantidad = (Integer) body.get("cantidad");
            var result = carritoService.actualizarCantidad(carritoId, usuario.userId(), productoId, cantidad,
                    text(body, "deviceId", "legacy-web"), number(body, "lamportTimestamp", 0),
                    text(body, "operationId", UUID.randomUUID().toString()));
            HttpStatus status = result.accepted() ? HttpStatus.OK : HttpStatus.CONFLICT;
            return ResponseEntity.status(status).header("X-Trace-Id", traceId).body(result);
        } finally {
            TraceContext.clear();
        }
    }

    private static String traceId(String incomingTraceId) {
        return incomingTraceId == null || incomingTraceId.isBlank()
                ? UUID.randomUUID().toString() : incomingTraceId.trim();
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

    private static String text(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key); return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static long number(Map<String, Object> body, String key, long fallback) {
        Object value = body.get(key); return value instanceof Number number ? number.longValue() : fallback;
    }
}
