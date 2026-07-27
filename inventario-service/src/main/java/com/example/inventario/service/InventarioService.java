package com.example.inventario.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

@Service
public class InventarioService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public InventarioService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listarMovimientos() {
        return jdbc.queryForList("select * from inventario.fn_mostrar_movimientos_inventario()");
    }

    public List<Map<String, Object>> listarSubtipos(Integer tipo) {
        return jdbc.queryForList("select * from inventario.fn_subtipos_movimiento(?::integer)", tipo);
    }

    public Map<String, Object> obtenerStock(Integer productoId) {
        if (productoId == null) {
            throw new IllegalArgumentException("Falta productoId");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from inventario.fn_obtener_stock(?::integer)", productoId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto " + productoId + " no existe");
        }
        return rows.getFirst();
    }

    public List<Map<String, Object>> listarStock(List<Integer> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos un productoId");
        }

        List<Integer> ids = productoIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos un productoId valido");
        }

        return jdbc.queryForList(
                "select * from inventario.fn_listar_stock_json(?::jsonb)",
                toJson(ids));
    }

    @Transactional
    public void registrarMovimiento(Object body, String usuario) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (CallableStatement statement = connection.prepareCall(
                    "call inventario.sp_movimiento_inventario_json(?::jsonb, ?)")) {
                statement.setString(1, toJson(body));
                if (usuario == null || usuario.isBlank()) {
                    statement.setNull(2, Types.VARCHAR);
                } else {
                    statement.setString(2, usuario);
                }
                statement.execute();
                return null;
            }
        });
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body == null ? Map.of() : body);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Body JSON invalido", ex);
        }
    }
}
