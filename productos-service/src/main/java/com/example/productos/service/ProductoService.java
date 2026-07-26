package com.example.productos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        out.put("galeria", jdbc.queryForList("select * from productos.fn_producto_galeria(?)", id));
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
}
