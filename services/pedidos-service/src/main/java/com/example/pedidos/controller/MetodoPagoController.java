package com.example.pedidos.controller;

import com.example.pedidos.model.MetodoPago;
import com.example.pedidos.model.TipoMetodoPago;
import com.example.pedidos.paging.PageResponse;
import com.example.pedidos.paging.Paginacion;
import com.example.pedidos.security.AuthUsuario;
import com.example.pedidos.security.AuthenticatedUser;
import com.example.pedidos.service.MetodoPagoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metodopago")
public class MetodoPagoController {

    private final MetodoPagoService metodoPagoService;

    @Autowired
    public MetodoPagoController(MetodoPagoService metodoPagoService) {
        this.metodoPagoService = metodoPagoService;
    }

    @GetMapping("/usuario/{usuarioId}")
    public PageResponse<MetodoPago> listarPorUsuario(@PathVariable Integer usuarioId,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size,
                                                       @AuthUsuario AuthenticatedUser usuario) {
        verificarPropioUsuario(usuarioId, usuario);
        return metodoPagoService.listarPorUsuario(usuarioId, Paginacion.de(page, size));
    }

    @GetMapping("/tipos")
    public List<TipoMetodoPago> listarTipos() {
        return metodoPagoService.listarTipos();
    }

    @GetMapping("/{metodopagoId}")
    public MetodoPago obtenerPorId(@PathVariable Integer metodopagoId, @AuthUsuario AuthenticatedUser usuario) {
        MetodoPago metodoPago = metodoPagoService.obtenerPorIdYUsuario(metodopagoId, usuario.userId());
        if (metodoPago == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Metodo de pago no encontrado");
        }
        return metodoPago;
    }

    @PostMapping
    public ResponseEntity<Void> agregar(@RequestBody Map<String, Object> body, @AuthUsuario AuthenticatedUser usuario) {
        String numeroTarjeta = (String) body.get("numeroTarjeta");
        LocalDate fechaExpiracion = LocalDate.parse((String) body.get("fechaExpiracion"));
        Integer tipoId = (Integer) body.get("tipoId");
        Integer metodopagoId = metodoPagoService.agregar(numeroTarjeta, fechaExpiracion, tipoId, usuario.userId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(metodopagoId)
                .toUri();
        return ResponseEntity.created(location).build();
    }

    @PutMapping("/{metodopagoId}")
    public ResponseEntity<Void> actualizar(@PathVariable Integer metodopagoId, @RequestBody Map<String, Object> body,
                                            @AuthUsuario AuthenticatedUser usuario) {
        String numeroTarjeta = (String) body.get("numeroTarjeta");
        String fechaStr = (String) body.get("fechaExpiracion");
        LocalDate fechaExpiracion = fechaStr != null ? LocalDate.parse(fechaStr) : null;
        Integer tipoId = (Integer) body.get("tipoId");
        Boolean habilitado = (Boolean) body.get("habilitado");
        metodoPagoService.actualizar(metodopagoId, usuario.userId(), numeroTarjeta, fechaExpiracion, tipoId, habilitado);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{metodopagoId}")
    public ResponseEntity<Void> inactivar(@PathVariable Integer metodopagoId, @AuthUsuario AuthenticatedUser usuario) {
        metodoPagoService.inactivar(metodopagoId, usuario.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{metodopagoId}/reactivar")
    public ResponseEntity<Void> reactivar(@PathVariable Integer metodopagoId, @AuthUsuario AuthenticatedUser usuario) {
        metodoPagoService.reactivar(metodopagoId, usuario.userId());
        return ResponseEntity.noContent().build();
    }

    private void verificarPropioUsuario(Integer usuarioId, AuthenticatedUser usuario) {
        if (!usuarioId.equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
        }
    }
}
