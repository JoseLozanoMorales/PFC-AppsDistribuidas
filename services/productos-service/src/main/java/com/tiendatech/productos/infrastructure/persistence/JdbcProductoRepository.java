package com.tiendatech.productos.infrastructure.persistence;

import com.tiendatech.productos.domain.ImagenProducto;
import com.tiendatech.productos.domain.MediaProducto;
import com.tiendatech.productos.domain.ProductoRepository;
import com.tiendatech.productos.domain.ProductoResumen;
import com.tiendatech.productos.domain.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class JdbcProductoRepository implements ProductoRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcProductoRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ProductoResumen> listar(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return jdbc.query("""
                select producto_id, nombre, preciounitario, enlace, fecha, stock,
                       marca_id, gama_id, iva_id, costo, habilitado,
                       (SELECT MIN(g.galeria_id) FROM productos.galeria_productos_v2 g
                        WHERE g.producto_id = p.producto_id AND g.habilitado) AS galeria_id
                from productos.producto p
                where p.habilitado
                order by producto_id
                limit ? offset ?
                """,
                (rs, rowNum) -> new ProductoResumen(
                        rs.getLong("producto_id"),
                        rs.getString("nombre"),
                        rs.getBigDecimal("preciounitario"),
                        rs.getString("enlace"),
                        rs.getObject("fecha", java.time.LocalDate.class),
                        rs.getInt("stock"),
                        nullableLong(rs.getObject("marca_id")),
                        nullableLong(rs.getObject("gama_id")),
                        nullableLong(rs.getObject("iva_id")),
                        rs.getBigDecimal("costo"),
                        rs.getBoolean("habilitado"),
                        nullableLong(rs.getObject("galeria_id"))),
                safeSize, safePage * safeSize);
    }

    public List<Map<String, Object>> resumirVentas(List<Map<String, Object>> ventas) {
        return ventas.stream().map(venta -> {
            long id = ((Number) venta.get("productoId")).longValue();
            List<Map<String, Object>> producto = jdbc.queryForList("""
                    SELECT producto_id AS "productoId", nombre,
                           preciounitario AS precio
                    FROM productos.producto
                    WHERE producto_id = ? AND habilitado
                    """, id);
            if (producto.isEmpty()) return null;
            Map<String, Object> resultado = new LinkedHashMap<>(producto.getFirst());
            resultado.put("unidades", venta.get("unidades"));
            return resultado;
        }).filter(java.util.Objects::nonNull).toList();
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

    public MediaProducto galeriaContenido(Integer galeriaId) {
        List<MediaProducto> rows = jdbc.query(
                "select contenido, mime_type, peso_bytes from productos.galeria_productos_v2 where galeria_id = ? and habilitado",
                (rs, i) -> new MediaProducto(
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

    public List<Map<String, Object>> buscar(Map<String, Object> filtros) {
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

    public List<Map<String, Object>> porCategoria(Integer categoriaId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return jdbc.queryForList(
                detalleSql() + " WHERE p.categoria_id = ? AND p.habilitado ORDER BY p.nombre LIMIT ? OFFSET ?",
                categoriaId, safeSize, safePage * safeSize);
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

    // Un producto nace sin existencias y sin costo: el stock solo lo mueve una recepcion de
    // orden de compra y el costo lo recalcula inventario-service (promedio ponderado) a partir
    // de ahi. Por eso "stock", "costo" y "valor_inventario" del payload se ignoran a proposito
    // aca (incluso si algun endpoint legado los manda) -- nacen siempre en 0.
    @Transactional
    public Long crear(Integer categoriaId, Map<String, Object> body, String usuario) {
        Map<String, Object> payload = new LinkedHashMap<>(body == null ? Map.of() : body);
        Map<String, Object> atributos = extraerAtributos(payload);
        return jdbc.queryForObject("""
                INSERT INTO productos.producto
                    (nombre, preciounitario, enlace, fecha, stock, marca_id,
                     gama_id, iva_id, costo, habilitado, categoria_id, valor_inventario, atributos)
                VALUES (?, ?, ?, current_date(), 0, ?, ?, ?, 0, true, ?, 0, CAST(? AS JSONB))
                RETURNING producto_id
                """, Long.class,
                payload.get("nombre"), payload.get("preciounitario"), payload.get("enlace"),
                payload.get("marca_id"), payload.get("gama_id"),
                payload.get("iva_id"), categoriaId, json(atributos));
    }

    @Transactional
    public Long agregarImagen(Integer productoId, ImagenProducto imagen, String descripcion, boolean portada) {
        validarImagen(imagen);
        String mime = mimeType(imagen);
        byte[] content = imagen.contenido();
        byte[] hash = sha256(content);
        List<Long> existing = buscarImagenExistente(productoId, hash, content);
        if (!existing.isEmpty()) {
            return reutilizarImagenExistente(productoId, existing.get(0), portada);
        }
        return insertarImagen(productoId, imagen, descripcion, portada, mime, hash, content);
    }

    private static void validarImagen(ImagenProducto imagen) {
        if (imagen == null || imagen.estaVacia()) throw new IllegalArgumentException("La imagen esta vacia");
        String mime = mimeType(imagen);
        if (!mime.startsWith("image/")) throw new IllegalArgumentException("Solo se permiten imagenes");
        if (imagen.pesoBytes() > 8L * 1024 * 1024) throw new IllegalArgumentException("La imagen supera 8 MB");
    }

    private static String mimeType(ImagenProducto imagen) {
        return imagen.mimeType() == null ? "application/octet-stream" : imagen.mimeType();
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible calcular la huella de la imagen", ex);
        }
    }

    private List<Long> buscarImagenExistente(Integer productoId, byte[] hash, byte[] content) {
        return jdbc.queryForList("""
                SELECT galeria_id FROM productos.galeria_productos_v2
                WHERE producto_id=? AND habilitado AND (hash_sha256=? OR contenido=?)
                ORDER BY galeria_id LIMIT 1
                """, Long.class, productoId, hash, content);
    }

    private Long reutilizarImagenExistente(Integer productoId, Long galeriaId, boolean portada) {
        if (portada) {
            jdbc.update("UPDATE productos.galeria_productos_v2 SET es_portada=false, para_menu=false WHERE producto_id=?", productoId);
            jdbc.update("UPDATE productos.galeria_productos_v2 SET es_portada=true, para_menu=true WHERE galeria_id=?", galeriaId);
        }
        return galeriaId;
    }

    private Long insertarImagen(Integer productoId, ImagenProducto imagen, String descripcion, boolean portada,
                                String mime, byte[] hash, byte[] content) {
        if (portada) {
            jdbc.update("UPDATE productos.galeria_productos_v2 SET es_portada=false WHERE producto_id=?", productoId);
        }
        return jdbc.queryForObject("""
                INSERT INTO productos.galeria_productos_v2
                    (producto_id, descripcion, habilitado, es_portada, para_galeria, para_menu,
                     posicion_galeria, mime_type, peso_bytes, hash_sha256, contenido)
                VALUES (?, ?, true, ?, true, ?,
                        COALESCE((SELECT max(posicion_galeria) + 1 FROM productos.galeria_productos_v2 WHERE producto_id=?), 1),
                        ?, ?, ?, ?) RETURNING galeria_id
                """, Long.class, productoId, descripcion, portada, portada, productoId,
                mime, imagen.pesoBytes(), hash, content);
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

    // "stock" y "costo" quedaron fuera a proposito: ya no son editables a mano desde aca,
    // solo cambian via movimientos de inventario (ver inventario-service). Ya no bloqueamos
    // con un CHECK si el precio queda por debajo del costo: en vez de rechazar la edicion,
    // el producto se auto-desactiva (habilitado=false) para que desaparezca del catalogo del
    // cliente pero el admin lo siga viendo y pueda corregir el precio y reactivarlo (ver
    // activar()). Si ya estaba deshabilitado por otra razon, esto no lo reactiva solo.
    @Transactional
    public void actualizarBasico(Integer productoId, Map<String, Object> body, String usuario) {
        Map<String, Object> payload = new LinkedHashMap<>(body == null ? Map.of() : body);
        Map<String, Object> atributos = extraerAtributos(payload);
        int actualizados = jdbc.update("""
                UPDATE productos.producto SET
                    nombre = COALESCE(?, nombre),
                    preciounitario = COALESCE(?, preciounitario),
                    enlace = COALESCE(?, enlace),
                    marca_id = COALESCE(?, marca_id), gama_id = COALESCE(?, gama_id),
                    iva_id = COALESCE(?, iva_id),
                    categoria_id = COALESCE(?, categoria_id),
                    atributos = atributos || CAST(? AS JSONB),
                    habilitado = CASE WHEN COALESCE(?, preciounitario) < costo THEN false ELSE habilitado END
                WHERE producto_id = ?
                """, payload.get("nombre"), payload.get("preciounitario"), payload.get("enlace"),
                payload.get("marca_id"), payload.get("gama_id"),
                payload.get("iva_id"), payload.get("categoria_id"),
                json(atributos), payload.get("preciounitario"), productoId);
        if (actualizados == 0) throw new ResourceNotFoundException("Producto no encontrado");
    }

    // Reactivacion manual y explicita: nunca automatica, para no revivir por accidente un
    // producto que el admin deshabilito por otro motivo (descontinuado, fuera de temporada,
    // etc). Exige que el precio ya sea >= costo; si no, el admin tiene que repreciar primero.
    @Transactional
    public void activar(Integer productoId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT preciounitario, costo FROM productos.producto WHERE producto_id = ?", productoId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Producto no encontrado");
        java.math.BigDecimal precio = (java.math.BigDecimal) rows.get(0).get("preciounitario");
        java.math.BigDecimal costo = (java.math.BigDecimal) rows.get(0).get("costo");
        if (precio.compareTo(costo) < 0) {
            throw new IllegalArgumentException(
                    "No se puede activar: el precio ($" + precio + ") sigue por debajo del costo ($" + costo
                            + "). Actualiza el precio antes de reactivar el producto.");
        }
        jdbc.update("UPDATE productos.producto SET habilitado = true WHERE producto_id = ?", productoId);
    }

    private static String detalleSql() {
        return """
                SELECT p.producto_id AS id, p.producto_id, p.nombre,
                       p.preciounitario AS precio, p.preciounitario, p.enlace,
                       p.fecha, p.stock, p.marca_id, m.nombre AS marca,
                       p.gama_id, g.tipo_gama AS gama, p.iva_id, i.porcentaje AS iva,
                       p.costo, p.habilitado, p.categoria_id,
                       c.nombre AS categoria, p.valor_inventario, p.atributos,
                       (SELECT image.galeria_id FROM productos.galeria_productos_v2 image
                        WHERE image.producto_id = p.producto_id AND image.habilitado
                        ORDER BY image.es_portada DESC, image.para_menu DESC,
                                 image.posicion_galeria, image.galeria_id LIMIT 1) AS galeria_id
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

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("No fue posible serializar los atributos del producto", ex);
        }
    }
}
