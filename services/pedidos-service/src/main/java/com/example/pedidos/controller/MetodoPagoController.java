package com.example.pedidos.controller;

import com.example.pedidos.model.MetodoPago;
import com.example.pedidos.model.TipoMetodoPago;
import com.example.pedidos.security.AuthUsuario;
import com.example.pedidos.security.AuthenticatedUser;
import com.example.pedidos.service.MetodoPagoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public List<MetodoPago> listarPorUsuario(@PathVariable Integer usuarioId, @AuthUsuario AuthenticatedUser usuario) {
        verificarPropioUsuario(usuarioId, usuario);
        return metodoPagoService.listarPorUsuario(usuarioId);
    }

    @GetMapping("/tipos")
    public List<TipoMetodoPago> listarTipos() {
        return metodoPagoService.listarTipos();
    }

    @PostMapping
    public void agregar(@RequestBody Map<String, Object> body, @AuthUsuario AuthenticatedUser usuario) {
        String numeroTarjeta = (String) body.get("numeroTarjeta");
        LocalDate fechaExpiracion = LocalDate.parse((String) body.get("fechaExpiracion"));
        Integer tipoId = (Integer) body.get("tipoId");
        metodoPagoService.agregar(numeroTarjeta, fechaExpiracion, tipoId, usuario.userId());
    }

    @PutMapping("/{metodopagoId}")
    public void actualizar(@PathVariable Integer metodopagoId, @RequestBody Map<String, Object> body,
                            @AuthUsuario AuthenticatedUser usuario) {
        String numeroTarjeta = (String) body.get("numeroTarjeta");
        String fechaStr = (String) body.get("fechaExpiracion");
        LocalDate fechaExpiracion = fechaStr != null ? LocalDate.parse(fechaStr) : null;
        Integer tipoId = (Integer) body.get("tipoId");
        Boolean habilitado = (Boolean) body.get("habilitado");
        metodoPagoService.actualizar(metodopagoId, usuario.userId(), numeroTarjeta, fechaExpiracion, tipoId, habilitado);
    }

    @DeleteMapping("/{metodopagoId}")
    public void inactivar(@PathVariable Integer metodopagoId, @AuthUsuario AuthenticatedUser usuario) {
        metodoPagoService.inactivar(metodopagoId, usuario.userId());
    }

    @PostMapping("/{metodopagoId}/reactivar")
    public void reactivar(@PathVariable Integer metodopagoId, @AuthUsuario AuthenticatedUser usuario) {
        metodoPagoService.reactivar(metodopagoId, usuario.userId());
    }

    private void verificarPropioUsuario(Integer usuarioId, AuthenticatedUser usuario) {
        if (!usuarioId.equals(usuario.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
        }
    }
}
