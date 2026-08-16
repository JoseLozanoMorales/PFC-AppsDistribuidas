package com.example.productos.controller;

import com.example.productos.service.ProductoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/api/productos")
    public ResponseEntity<Map<String, Object>> crearProducto(@RequestBody Map<String, Object> body,
                                                              @RequestHeader(value = "X-Usuario", required = false) String usuario)
            throws JsonProcessingException {
        Integer categoriaId = Integer.valueOf(String.valueOf(body.get("categoria_id")));
        Long productoId = service.crear(categoriaId, body, usuario);
        return ResponseEntity.ok(Map.of("ok", true, "productoId", productoId));
    }

    @PostMapping(value = "/api/productos/{id}/galeria", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> agregarImagen(@PathVariable Integer id,
                                                              @RequestPart("file") MultipartFile file,
                                                              @RequestParam(required = false) String descripcion,
                                                              @RequestParam(defaultValue = "false") boolean portada) throws Exception {
        Long galeriaId = service.agregarImagen(id, file, descripcion, portada);
        return ResponseEntity.ok(Map.of("ok", true, "galeriaId", galeriaId));
    }

    @DeleteMapping("/api/galeria_v2/{id}")
    public ResponseEntity<Map<String, Boolean>> quitarImagen(@PathVariable Integer id) {
        service.quitarImagen(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PatchMapping("/api/productos/{id}/galeria/orden")
    public ResponseEntity<Map<String, Boolean>> ordenarGaleria(@PathVariable Integer id,
                                                               @RequestBody Map<String, List<Integer>> body) {
        service.ordenarGaleria(id, body.getOrDefault("ids", List.of()));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/api/productos/mas-vendidos")
    public List<Map<String, Object>> masVendidos(@RequestParam(defaultValue = "3") int limite) {
        return service.masVendidos(limite);
    }

    @GetMapping("/api/productos/recientes-menu")
    public List<Map<String, Object>> recientesMenu(@RequestParam(defaultValue = "5") int limit) {
        return service.recientesMenu(limit);
    }

    @GetMapping("/api/categorias")
    public List<Map<String, Object>> categorias() {
        return service.categorias();
    }

    @GetMapping("/api/marcas")
    public List<Map<String, Object>> marcas() {
        return service.marcas();
    }

    @GetMapping("/api/gamas")
    public List<Map<String, Object>> gamas() {
        return service.gamas();
    }

    @GetMapping("/api/galeria/{galeriaId}/contenido")
    public ResponseEntity<ByteArrayResource> galeriaContenido(@PathVariable Integer galeriaId) {
        ProductoService.Media media = service.galeriaContenido(galeriaId);
        if (media == null || media.bytes() == null || media.bytes().length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(mediaType(media.mimeType()))
                .contentLength(media.length() != null ? media.length() : media.bytes().length)
                .body(new ByteArrayResource(media.bytes()));
    }

    @RequestMapping(path = "/api/galeria/{galeriaId}/contenido", method = RequestMethod.HEAD)
    public ResponseEntity<Void> galeriaContenidoHead(@PathVariable Integer galeriaId) {
        ProductoService.Media media = service.galeriaContenido(galeriaId);
        if (media == null || media.bytes() == null || media.bytes().length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/galeria/{galeriaId}/media")
    public ResponseEntity<ByteArrayResource> galeriaMedia(@PathVariable Integer galeriaId) {
        return galeriaContenido(galeriaId);
    }

    @GetMapping("/api/galeria/img/{galeriaId}")
    public ResponseEntity<ByteArrayResource> galeriaImagen(@PathVariable Integer galeriaId) {
        return galeriaContenido(galeriaId);
    }

    @GetMapping("/api/galeria_v2/img/{galeriaId}")
    public ResponseEntity<ByteArrayResource> galeriaV2Imagen(@PathVariable Integer galeriaId) {
        return galeriaContenido(galeriaId);
    }

    @RequestMapping(path = "/api/galeria_v2/img/{galeriaId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> galeriaV2ImagenHead(@PathVariable Integer galeriaId) {
        return galeriaContenidoHead(galeriaId);
    }

    @GetMapping("/api/galeria_v2/producto/{productoId}")
    public List<Map<String, Object>> galeriaV2Producto(
            @PathVariable Integer productoId,
            @RequestParam(required = false) String scope) {
        return service.galeriaProducto(productoId, scope);
    }

    @GetMapping("/api/productos/{productoId}/galeria/lista")
    public List<Map<String, Object>> galeriaProductoCompat(
            @PathVariable Integer productoId,
            @RequestParam(defaultValue = "galeria") String vista) {
        return service.galeriaProducto(productoId, vista);
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

    @PostMapping("/api/sp/accesorios")
    public ResponseEntity<Map<String, Boolean>> crearAccesorio(@RequestBody Map<String, Object> body,
                                                               @RequestHeader(value = "X-Usuario", required = false) String usuario)
            throws JsonProcessingException {
        service.crear(10, body, usuario);
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

    private static MediaType mediaType(String value) {
        try {
            return value != null && !value.isBlank()
                    ? MediaType.parseMediaType(value)
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
