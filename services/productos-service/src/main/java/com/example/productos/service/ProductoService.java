package com.example.productos.service;

import com.example.productos.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.ArrayList;
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
        return jdbc.queryForList("""
                SELECT p.producto_id, p.nombre, p.preciounitario AS precio,
                       sum(fc.cantidad) AS unidades
                FROM ventas.factura_cuerpo fc
                JOIN productos.producto p ON p.producto_id = fc.producto_id
                WHERE p.habilitado
                GROUP BY p.producto_id, p.nombre, p.preciounitario
                ORDER BY unidades DESC, p.producto_id
                LIMIT ?
                """, Math.max(limite, 1));
    }

    public List<Map<String, Object>> recientesMenu(int limit) {
        int safeLimit = (limit <= 0 || limit > 10) ? 5 : limit;
        return jdbc.queryForList("""
                SELECT p.producto_id, p.nombre, p.preciounitario AS precio, p.fecha,
                       (SELECT g.galeria_id FROM productos.galeria_productos_v2 g
                        WHERE g.producto_id = p.producto_id AND g.habilitado
                          AND (g.para_menu OR g.es_portada)
                        ORDER BY g.es_portada DESC, g.posicion_menu, g.galeria_id LIMIT 1) AS galeria_id,
                       (SELECT g.mime_type FROM productos.galeria_productos_v2 g
                        WHERE g.producto_id = p.producto_id AND g.habilitado
                          AND (g.para_menu OR g.es_portada)
                        ORDER BY g.es_portada DESC, g.posicion_menu, g.galeria_id LIMIT 1) AS mime_type
                FROM productos.producto p
                WHERE p.habilitado AND p.stock > 0
                ORDER BY p.fecha DESC, p.producto_id DESC
                LIMIT ?
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
        return jdbc.queryForList("""
                SELECT id_categoria, nombre FROM productos.categoria_producto
                WHERE habilitado ORDER BY nombre
                """).stream().map(row -> {
            String nombre = String.valueOf(row.get("nombre"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", row.get("id_categoria"));
            out.put("id_categoria", row.get("id_categoria"));
            out.put("nombre", nombre);
            out.put("slug", slug(nombre));
            return out;
                }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> marcas() {
        return jdbc.queryForList("""
                SELECT marca_id AS id, marca_id, nombre FROM productos.marca
                WHERE habilitado ORDER BY nombre
                """);
    }

    public List<Map<String, Object>> gamas() {
        return jdbc.queryForList("""
                SELECT gama_id AS id, gama_id, tipo_gama AS nombre, precio_ensamblado
                FROM productos.gama WHERE habilitado ORDER BY gama_id
                """);
    }

    public Media galeriaContenido(Integer galeriaId) {
        List<Media> rows = jdbc.query(
                "select contenido, mime_type, peso_bytes from productos.galeria_productos_v2 where galeria_id = ? and habilitado",
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
                FROM productos.galeria_productos_v2
                WHERE producto_id = ? AND habilitado
                  AND (CASE
                       WHEN lower(coalesce(?, '')) = 'menu' THEN para_menu
                       WHEN lower(coalesce(?, '')) IN ('galeria', 'gallery') THEN para_galeria
                       ELSE true END)
                ORDER BY coalesce(posicion_galeria, posicion_menu, galeria_id), galeria_id
                """, productoId, scope, scope).stream().map(row -> {
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
        Map<String, Object> f = filtros == null ? Map.of() : filtros;
        StringBuilder sql = new StringBuilder("""
                SELECT p.producto_id AS id, p.producto_id, p.nombre,
                       p.preciounitario AS precio, p.costo, p.stock, p.fecha,
                       m.nombre AS marca, c.nombre AS categoria
                FROM productos.producto p
                JOIN productos.marca m ON m.marca_id = p.marca_id
                LEFT JOIN productos.categoria_producto c ON c.id_categoria = p.categoria_id
                WHERE p.habilitado
                """);
        List<Object> args = new ArrayList<>();
        agregarFiltro(sql, args, f, "categoria_id", " AND p.categoria_id = ?");
        agregarFiltro(sql, args, f, "stock_min", " AND p.stock >= ?");
        agregarFiltro(sql, args, f, "stock_max", " AND p.stock <= ?");
        agregarFiltro(sql, args, f, "fecha_min", " AND p.fecha >= ?");
        agregarFiltro(sql, args, f, "fecha_max", " AND p.fecha <= ?");
        Object q = f.get("q");
        if (q != null && !q.toString().isBlank()) {
            sql.append(" AND (lower(p.nombre) LIKE ? OR CAST(p.producto_id AS STRING) = ?)");
            args.add("%" + q.toString().toLowerCase() + "%");
            args.add(q.toString());
        }
        sql.append(" ORDER BY p.nombre");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> porCategoria(Integer categoriaId) {
        return jdbc.queryForList(detalleSql() + " WHERE p.categoria_id = ? AND p.habilitado ORDER BY p.nombre", categoriaId);
    }

    public Map<String, Object> detalle(Integer id) {
        List<Map<String, Object>> base = jdbc.queryForList(detalleSql() + " WHERE p.producto_id = ?", id);
        if (base.isEmpty()) {
            throw new ResourceNotFoundException("Producto no encontrado");
        }

        Map<String, Object> out = new LinkedHashMap<>(base.get(0));
        out.put("galeria", jdbc.queryForList("""
                SELECT galeria_id, descripcion, es_portada, para_galeria, para_menu,
                       posicion_galeria, posicion_menu, mime_type, peso_bytes, ancho, alto
                FROM productos.galeria_productos_v2
                WHERE producto_id = ? AND habilitado ORDER BY galeria_id
                """, id).stream()
                .map(this::normalizeGaleriaRow)
                .collect(Collectors.toList()));
        return out;
    }

    @Transactional
    public Long crear(Integer categoriaId, Map<String, Object> body, String usuario) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>(body == null ? Map.of() : body);
        Map<String, Object> atributos = extraerAtributos(payload);
        return jdbc.queryForObject("""
                INSERT INTO productos.producto
                    (nombre, preciounitario, enlace, fecha, stock, marca_id,
                     gama_id, iva_id, costo, habilitado, categoria_id, valor_inventario, atributos)
                VALUES (?, ?, ?, current_date(), ?, ?, ?, ?, ?, true, ?, ?, CAST(? AS JSONB))
                RETURNING producto_id
                """, Long.class,
                payload.get("nombre"), payload.get("preciounitario"), payload.get("enlace"),
                valor(payload, "stock", 0), payload.get("marca_id"), payload.get("gama_id"),
                payload.get("iva_id"), valor(payload, "costo", 0), categoriaId,
                valor(payload, "valor_inventario", 0), objectMapper.writeValueAsString(atributos));
    }

    @Transactional
    public Long agregarImagen(Integer productoId, MultipartFile file, String descripcion, boolean portada) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("La imagen está vacía");
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!mime.startsWith("image/")) throw new IllegalArgumentException("Solo se permiten imágenes");
        if (file.getSize() > 8L * 1024 * 1024) throw new IllegalArgumentException("La imagen supera 8 MB");
        if (portada) jdbc.update("UPDATE productos.galeria_productos_v2 SET es_portada=false WHERE producto_id=?", productoId);
        return jdbc.queryForObject("""
                INSERT INTO productos.galeria_productos_v2
                    (producto_id, descripcion, habilitado, es_portada, para_galeria, para_menu,
                     posicion_galeria, mime_type, peso_bytes, contenido)
                VALUES (?, ?, true, ?, true, ?,
                        COALESCE((SELECT max(posicion_galeria) + 1 FROM productos.galeria_productos_v2 WHERE producto_id=?), 1),
                        ?, ?, ?) RETURNING galeria_id
                """, Long.class, productoId, descripcion, portada, portada, productoId,
                mime, file.getSize(), file.getBytes());
    }

    @Transactional
    public void quitarImagen(Integer galeriaId) {
        jdbc.update("UPDATE productos.galeria_productos_v2 SET habilitado=false WHERE galeria_id=?", galeriaId);
    }

    @Transactional
    public void ordenarGaleria(Integer productoId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        Integer disponibles = jdbc.queryForObject("""
                SELECT count(*) FROM productos.galeria_productos_v2
                WHERE producto_id=? AND habilitado AND galeria_id IN (%s)
                """.formatted(String.join(",", java.util.Collections.nCopies(ids.size(), "?"))),
                Integer.class, joinArgs(productoId, ids));
        if (disponibles == null || disponibles != ids.size()) {
            throw new IllegalArgumentException("La selección contiene imágenes ajenas al producto");
        }
        jdbc.update("UPDATE productos.galeria_productos_v2 SET es_portada=false, para_menu=false WHERE producto_id=?", productoId);
        for (int index = 0; index < ids.size(); index++) {
            boolean first = index == 0;
            jdbc.update("""
                    UPDATE productos.galeria_productos_v2
                    SET posicion_galeria=?, posicion_menu=?, es_portada=?, para_menu=?
                    WHERE producto_id=? AND galeria_id=? AND habilitado
                    """, index + 1, index + 1, first, first, productoId, ids.get(index));
        }
    }

    private static Object[] joinArgs(Integer productoId, List<Integer> ids) {
        Object[] args = new Object[ids.size() + 1];
        args[0] = productoId;
        for (int i = 0; i < ids.size(); i++) args[i + 1] = ids.get(i);
        return args;
    }

    @Transactional
    public void eliminar(Integer productoId, String usuario) {
        int actualizados = jdbc.update("UPDATE productos.producto SET habilitado = false WHERE producto_id = ?", productoId);
        if (actualizados == 0) throw new ResourceNotFoundException("Producto no encontrado");
    }

    public List<Map<String, Object>> detalleParaEditar(Integer id) {
        return jdbc.queryForList(detalleSql() + " WHERE p.producto_id = ?", id);
    }

    public List<Map<String, Object>> listarIvas() {
        return jdbc.queryForList("SELECT iva_id, porcentaje, habilitado FROM productos.iva WHERE habilitado ORDER BY porcentaje");
    }

    @Transactional
    public void actualizarBasico(Integer productoId, Map<String, Object> body, String usuario) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>(body == null ? Map.of() : body);
        Map<String, Object> atributos = extraerAtributos(payload);
        int actualizados = jdbc.update("""
                UPDATE productos.producto SET
                    nombre = COALESCE(?, nombre),
                    preciounitario = COALESCE(?, preciounitario),
                    enlace = COALESCE(?, enlace), stock = COALESCE(?, stock),
                    marca_id = COALESCE(?, marca_id), gama_id = COALESCE(?, gama_id),
                    iva_id = COALESCE(?, iva_id), costo = COALESCE(?, costo),
                    categoria_id = COALESCE(?, categoria_id),
                    atributos = atributos || CAST(? AS JSONB)
                WHERE producto_id = ?
                """, payload.get("nombre"), payload.get("preciounitario"), payload.get("enlace"),
                payload.get("stock"), payload.get("marca_id"), payload.get("gama_id"),
                payload.get("iva_id"), payload.get("costo"), payload.get("categoria_id"),
                objectMapper.writeValueAsString(atributos), productoId);
        if (actualizados == 0) throw new ResourceNotFoundException("Producto no encontrado");
    }

    private static String detalleSql() {
        return """
                SELECT p.producto_id AS id, p.producto_id, p.nombre,
                       p.preciounitario AS precio, p.preciounitario, p.enlace,
                       p.fecha, p.stock, p.marca_id, m.nombre AS marca,
                       p.gama_id, g.tipo_gama AS gama, p.iva_id, i.porcentaje AS iva,
                       p.costo, p.habilitado, p.categoria_id,
                       c.nombre AS categoria, p.valor_inventario, p.atributos
                FROM productos.producto p
                JOIN productos.marca m ON m.marca_id = p.marca_id
                LEFT JOIN productos.gama g ON g.gama_id = p.gama_id
                JOIN productos.iva i ON i.iva_id = p.iva_id
                LEFT JOIN productos.categoria_producto c ON c.id_categoria = p.categoria_id
                """;
    }

    private static void agregarFiltro(StringBuilder sql, List<Object> args,
                                      Map<String, Object> filtros, String clave, String clausula) {
        Object valor = filtros.get(clave);
        if (valor != null && !valor.toString().isBlank()) {
            sql.append(clausula);
            args.add(valor);
        }
    }

    private static Object valor(Map<String, Object> payload, String clave, Object predeterminado) {
        return payload.get(clave) == null ? predeterminado : payload.get(clave);
    }

    private static Map<String, Object> extraerAtributos(Map<String, Object> payload) {
        Map<String, Object> atributos = new LinkedHashMap<>(payload);
        for (String comun : List.of("nombre", "preciounitario", "enlace", "fecha", "stock",
                "marca_id", "gama_id", "iva_id", "costo", "habilitado", "categoria_id",
                "valor_inventario", "usuario")) {
            atributos.remove(comun);
        }
        return atributos;
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
