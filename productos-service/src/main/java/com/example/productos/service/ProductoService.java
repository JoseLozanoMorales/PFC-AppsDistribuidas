package com.example.productos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductoService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ProductoService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listar(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return jdbc.queryForList("""
                select producto_id, nombre, preciounitario, enlace, fecha, stock,
                       marca_id, gama_id, iva_id, costo, habilitado
                from productos.producto
                order by producto_id
                limit ? offset ?
                """, safeSize, safePage * safeSize);
    }

    public List<Map<String, Object>> masVendidos(int limite) {
        return jdbc.queryForList("select * from productos.productos_mas_vendidos_menu(?)", Math.max(limite, 1));
    }

    public List<Map<String, Object>> recientesMenu(int limit) {
        int safeLimit = (limit <= 0 || limit > 10) ? 5 : limit;
        return jdbc.queryForList("""
                select producto_id, nombre, precio, fecha, galeria_id, mime_type
                from public.f_productos_recientes_con_imagen_menu(?)
                """, safeLimit).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("productoId", row.get("producto_id"));
            out.put("nombre", row.get("nombre"));
            out.put("precio", row.get("precio"));
            out.put("fecha", row.get("fecha"));
            out.put("galeriaId", row.get("galeria_id"));
            out.put("mimeType", row.get("mime_type"));
            return out;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> categorias() {
        return jdbc.queryForList("select * from public.fn_listar_categorias()").stream().map(row -> {
            String nombre = String.valueOf(row.get("nombre"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", row.get("id_categoria"));
            out.put("id_categoria", row.get("id_categoria"));
            out.put("nombre", nombre);
            out.put("slug", slug(nombre));
            return out;
        }).collect(Collectors.toList());
    }

    public Media galeriaContenido(Integer galeriaId) {
        List<Media> rows = jdbc.query(
                "select contenido, mime_type, peso_bytes from public.galeria_productos_v2 where galeria_id = ?",
                (rs, i) -> new Media(
                        rs.getBytes("contenido"),
                        rs.getString("mime_type"),
                        rs.getObject("peso_bytes") instanceof Number number ? number.longValue() : null
                ),
                galeriaId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> galeriaProducto(Integer productoId, String scope) {
        return jdbc.queryForList("""
                select galeria_id, descripcion, es_portada, para_galeria, para_menu,
                       posicion_galeria, posicion_menu, mime_type, peso_bytes, ancho, alto, habilitado
                from public.fn_galeria_v2_listar(?, ?)
                """, productoId, scope).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("id", row.get("galeria_id"));
            out.put("galeriaId", row.get("galeria_id"));
            out.put("esPortada", row.get("es_portada"));
            out.put("paraGaleria", row.get("para_galeria"));
            out.put("paraMenu", row.get("para_menu"));
            out.put("posicionGaleria", row.get("posicion_galeria"));
            out.put("posicionMenu", row.get("posicion_menu"));
            out.put("mimeType", row.get("mime_type"));
            out.put("pesoBytes", row.get("peso_bytes"));
            return out;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> buscar(Map<String, Object> filtros) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(filtros == null ? Map.of() : filtros);
        return jdbc.queryForList("select * from productos.fn_buscar_productos_json(?::jsonb)", json);
    }

    public List<Map<String, Object>> porCategoria(Integer categoriaId) {
        return jdbc.queryForList("select * from productos.fn_buscar_productos_por_categoria(?)", categoriaId);
    }

    public Map<String, Object> detalle(Integer id) {
        List<Map<String, Object>> base = jdbc.queryForList("select * from productos.fn_producto_detalle(?)", id);
        if (base.isEmpty()) {
            throw new IllegalArgumentException("Producto no encontrado");
        }

        Map<String, Object> out = new LinkedHashMap<>(base.get(0));
        out.put("galeria", jdbc.queryForList("select * from productos.fn_producto_galeria(?)", id).stream()
                .map(this::normalizeGaleriaRow)
                .collect(Collectors.toList()));
        return out;
    }

    @Transactional
    public void crear(Integer categoriaId, Map<String, Object> body, String usuario) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>(body == null ? Map.of() : body);
        payload.put("categoria_id", categoriaId);
        if (usuario != null && !usuario.isBlank()) {
            payload.put("usuario", usuario);
        }
        jdbc.update("call productos.sp_agregar_producto_v2_json(?::jsonb, ?)",
                objectMapper.writeValueAsString(payload), usuario);
    }

    @Transactional
    public void eliminar(Integer productoId, String usuario) {
        jdbc.update("call productos.sp_eliminar_producto_v2(?, ?)", productoId, usuario);
    }

    public List<Map<String, Object>> detalleParaEditar(Integer id) {
        return jdbc.queryForList("select * from productos.fn_producto_detalle_actualizar(?)", id);
    }

    public List<Map<String, Object>> listarIvas() {
        return jdbc.queryForList("select * from productos.fn_listar_ivas()");
    }

    @Transactional
    public void actualizarBasico(Integer productoId, Map<String, Object> body, String usuario) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>(body == null ? Map.of() : body);
        payload.put("producto_id", productoId);
        if (usuario != null && !usuario.isBlank()) {
            payload.put("usuario", usuario);
        }
        jdbc.update("call productos.sp_actualizar_producto_v2_json(?::jsonb, ?)",
                objectMapper.writeValueAsString(payload), usuario);
    }

    private static String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^\\w]+", "-").toLowerCase().replaceAll("(^-|-$)", "");
    }

    private Map<String, Object> normalizeGaleriaRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("id", row.get("galeria_id"));
        out.put("galeriaId", row.get("galeria_id"));
        out.put("esPortada", row.get("es_portada"));
        out.put("paraGaleria", row.get("para_galeria"));
        out.put("posicionGaleria", row.get("posicion_galeria"));
        out.put("mimeType", row.get("mime_type"));
        return out;
    }

    public record Media(byte[] bytes, String mimeType, Long length) {
    }
}
