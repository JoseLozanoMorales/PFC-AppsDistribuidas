package com.example.pedidos.controller;

import com.example.pedidos.model.MetodoPago;
import com.example.pedidos.model.TipoMetodoPago;
import com.example.pedidos.service.MetodoPagoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public List<MetodoPago> listarPorUsuario(@PathVariable Integer usuarioId) {
        return metodoPagoService.listarPorUsuario(usuarioId);
    }

    @GetMapping("/tipos")
    public List<TipoMetodoPago> listarTipos() {
        return metodoPagoService.listarTipos();
    }

    @PostMapping
    public void agregar(@RequestBody Map<String, Object> body) {
        String numeroTarjeta = (String) body.get("numeroTarjeta");
        LocalDate fechaExpiracion = LocalDate.parse((String) body.get("fechaExpiracion"));
        Integer tipoId = (Integer) body.get("tipoId");
        Integer usuarioId = (Integer) body.get("usuarioId");
        metodoPagoService.agregar(numeroTarjeta, fechaExpiracion, tipoId, usuarioId);
    }

    @PutMapping("/{metodopagoId}")
    public void actualizar(@PathVariable Integer metodopagoId, @RequestBody Map<String, Object> body) {
        Integer usuarioId = (Integer) body.get("usuarioId");
        String numeroTarjeta = (String) body.get("numeroTarjeta");
        String fechaStr = (String) body.get("fechaExpiracion");
        LocalDate fechaExpiracion = fechaStr != null ? LocalDate.parse(fechaStr) : null;
        Integer tipoId = (Integer) body.get("tipoId");
        Boolean habilitado = (Boolean) body.get("habilitado");
        metodoPagoService.actualizar(metodopagoId, usuarioId, numeroTarjeta, fechaExpiracion, tipoId, habilitado);
    }

    @DeleteMapping("/{metodopagoId}")
    public void inactivar(@PathVariable Integer metodopagoId, @RequestParam Integer usuarioId) {
        metodoPagoService.inactivar(metodopagoId, usuarioId);
    }

    @PostMapping("/{metodopagoId}/reactivar")
    public void reactivar(@PathVariable Integer metodopagoId, @RequestParam Integer usuarioId) {
        metodoPagoService.reactivar(metodopagoId, usuarioId);
    }
}