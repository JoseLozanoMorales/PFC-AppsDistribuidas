package com.tiendatech.ventas.presentation;

import com.tiendatech.ventas.application.FacturaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/ventas")
public class VentasInternasController {
    private final FacturaService service;

    public VentasInternasController(FacturaService service) { this.service = service; }

    @GetMapping("/mas-vendidos")
    public List<Map<String, Object>> masVendidos(@RequestParam(defaultValue = "3") int limite) {
        return service.masVendidos(limite);
    }
}
