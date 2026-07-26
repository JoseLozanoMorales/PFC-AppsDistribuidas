package org.example.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.model.Factura;
import org.example.model.FacturaDetalle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import java.util.List;

@Repository
public class FacturaRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FacturaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    /** OJO: CALL sin llaves {} -- {call ...} rompe contra PROCEDURE reales (ver el bug que ya cazamos). */
    public Integer generarDesdeOrden(Integer ordenId) {
        String sql = "call ventas.sp_generar_factura_json(?, ?)";
        return jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall(sql)) {
                cs.setInt(1, ordenId);
                cs.registerOutParameter(2, Types.INTEGER);
                cs.execute();
                return cs.getInt(2);
            }
        });
    }

    public Factura obtenerPorId(Integer facturaId) {
        String sql = "SELECT ventas.fn_obtener_factura(?)::text";
        String json = jdbcTemplate.queryForObject(sql, String.class, facturaId);
        if (json == null || "null".equals(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Factura.class);
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo respuesta de fn_obtener_factura", e);
        }
    }

    public List<FacturaDetalle> listarDetalle(Integer facturaId) {
        String sql = "SELECT ventas.fn_listar_detalle_factura(?)::text";
        String json = jdbcTemplate.queryForObject(sql, String.class, facturaId);
        try {
            return objectMapper.readValue(json, new TypeReference<List<FacturaDetalle>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo respuesta de fn_listar_detalle_factura", e);
        }
    }

    /** usuarioId null -> trae todas (fn_listar_facturas tiene DEFAULT NULL). */
    public List<Factura> listar(Integer usuarioId) {
        String sql = "SELECT ventas.fn_listar_facturas(?)::text";
        String json = jdbcTemplate.queryForObject(sql, String.class, usuarioId);
        try {
            return objectMapper.readValue(json, new TypeReference<List<Factura>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo respuesta de fn_listar_facturas", e);
        }
    }
}