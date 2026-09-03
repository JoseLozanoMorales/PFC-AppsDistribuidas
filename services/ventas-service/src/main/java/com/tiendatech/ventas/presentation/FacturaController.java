package com.tiendatech.ventas.presentation;

import com.tiendatech.ventas.presentation.dto.FacturaDetalleResponse;
import com.tiendatech.ventas.presentation.dto.FacturaResponse;
import com.tiendatech.ventas.presentation.dto.GenerarFacturaRequest;
import com.tiendatech.ventas.domain.Factura;
import com.tiendatech.ventas.application.FacturaService;
import com.tiendatech.ventas.infrastructure.experiment.ExperimentFaultInjector;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/facturas")
public class FacturaController {

    private final FacturaService facturaService;
    private final ExperimentFaultInjector faultInjector;

    public FacturaController(FacturaService facturaService, ExperimentFaultInjector faultInjector) {
        this.facturaService = facturaService;
        this.faultInjector = faultInjector;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> generarDesdeOrden(@Valid @RequestBody GenerarFacturaRequest request,
            @RequestHeader(value = "X-Failure-Mode", defaultValue = "none") String failureMode) {
        faultInjector.apply(failureMode);
        Integer facturaId = facturaService.generar(request.toDomain());
        Factura factura = facturaService.obtenerPorId(facturaId);
        return ResponseEntity.created(URI.create("/api/facturas/" + facturaId))
                .body(Map.of("facturaId", facturaId, "numero", factura.getNumero(), "total", factura.getTotal()));
    }

    @GetMapping
    public List<FacturaResponse> listar(@RequestParam(required = false) Integer usuarioId) {
        return facturaService.listar(usuarioId).stream()
                .map(FacturaResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public FacturaResponse obtenerPorId(@PathVariable Integer id) {
        return FacturaResponse.from(facturaService.obtenerPorId(id));
    }

    @GetMapping("/{id}/detalle")
    public List<FacturaDetalleResponse> listarDetalle(@PathVariable Integer id) {
        return facturaService.listarDetalle(id).stream()
                .map(FacturaDetalleResponse::from)
                .collect(Collectors.toList());
    }
}
