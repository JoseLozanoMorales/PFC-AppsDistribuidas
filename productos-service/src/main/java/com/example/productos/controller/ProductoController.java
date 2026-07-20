package com.example.productos.controller;

import com.example.productos.service.ProductoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ProductoController {
    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @GetMapping("/api/productos")
    public List<Map<String, Object>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listar(page, size);
    }

    @GetMapping("/api/productos/mas-vendidos")
    public List<Map<String, Object>> masVendidos(@RequestParam(defaultValue = "3") int limite) {
        return service.masVendidos(limite);
    }

    @PostMapping("/api/productos/buscar")
    public List<Map<String, Object>> buscar(@RequestBody(required = false) Map<String, Object> filtros)
            throws JsonProcessingException {
        return service.buscar(filtros);
    }

    @GetMapping("/api/productos/por-categoria")
    public List<Map<String, Object>> porCategoria(@RequestParam Integer categoriaId) {
        return service.porCategoria(categoriaId);
    }

    @GetMapping("/api/productos/{id}")
    public Map<String, Object> detalle(@PathVariable Integer id) {
        return service.detalle(id);
    }

    @PostMapping("/api/sp/almacenamientos")
    public ResponseEntity<Map<String, Boolean>> crearAlmacenamiento(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) throws JsonProcessingException {
        service.crear(1, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/api/sp/cpu")
    public ResponseEntity<Map<String, Boolean>> crearCpu(@RequestBody Map<String, Object> body,
                                                         @RequestHeader(value = "X-Usuario", required = false) String usuario)
            throws JsonProcessingException {
        service.crear(2, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/api/sp/cpu-cooler")
    public ResponseEntity<Map<String, Boolean>> crearCpuCooler(@RequestBody Map<String, Object> body,
                                                               @RequestHeader(value = "X-Usuario", required = false) String usuario)
            throws JsonProcessingException {
        service.crear(3, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/api/sp/cubiertas")
    public ResponseEntity<Map<String, Boolean>> crearCubierta(@RequestBody Map<String, Object> body,
                                                              @RequestHeader(value = "X-Usuario", required = false) String usuario)
            throws JsonProcessingException {
        service.crear(4, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/api/sp/fuentes")
    public ResponseEntity<Map<String, Boolean>> crearFuente(@RequestBody Map<String, Object> body,
                                                            @RequestHeader(value = "X-Usuario", required = false) String usuario)
            throws JsonProcessingException {
        service.crear(5, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/api/sp/gpu")
    public ResponseEntity<Map<String, Boolean>> crearGpu(@RequestBody Map<String, Object> body,
                                                         @RequestHeader(value = "X-Usuario", required = false) String usuario)
            throws JsonProcessingException {
        service.crear(6, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/api/sp/ram")
    public ResponseEntity<Map<String, Boolean>> crearRam(@RequestBody Map<String, Object> body,
                                                         @RequestHeader(value = "X-Usuario", required = false) String usuario)
            throws JsonProcessingException {
        service.crear(7, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/api/sp/motherboards")
    public ResponseEntity<Map<String, Boolean>> crearMotherboard(@RequestBody Map<String, Object> body,
                                                                 @RequestHeader(value = "X-Usuario", required = false) String usuario)
            throws JsonProcessingException {
        service.crear(8, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/api/sp/perifericos")
    public ResponseEntity<Map<String, Boolean>> crearPeriferico(@RequestBody Map<String, Object> body,
                                                                @RequestHeader(value = "X-Usuario", required = false) String usuario)
            throws JsonProcessingException {
        service.crear(9, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/api/sp/productos/{id}")
    public ResponseEntity<Map<String, Boolean>> eliminar(@PathVariable Integer id,
                                                         @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        service.eliminar(id, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/api/sp/productos/{id}/editar")
    public ResponseEntity<?> detalleParaEditar(@PathVariable Integer id) {
        List<Map<String, Object>> rows = service.detalleParaEditar(id);
        return rows.isEmpty()
                ? ResponseEntity.status(404).body(Map.of("message", "Producto no encontrado"))
                : ResponseEntity.ok(rows.get(0));
    }

    @GetMapping("/api/sp/ivas")
    public List<Map<String, Object>> listarIvas() {
        return service.listarIvas();
    }

    @PutMapping("/api/sp/productos/{id}/basico")
    public ResponseEntity<Map<String, Boolean>> actualizarBasico(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) throws JsonProcessingException {
        service.actualizarBasico(id, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
