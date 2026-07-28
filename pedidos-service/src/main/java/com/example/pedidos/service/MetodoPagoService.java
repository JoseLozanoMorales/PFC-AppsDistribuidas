package com.example.pedidos.service;

import com.example.pedidos.model.MetodoPago;
import com.example.pedidos.model.TipoMetodoPago;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetodoPagoService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public MetodoPagoService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // Lista los metodos de pago enmascarados de un usuario.
    public List<MetodoPago> listarPorUsuario(Integer usuarioId) {
        String sql = "SELECT * FROM pedidos.fn_metodopago_por_usuario(?)";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MetodoPago(
                rs.getInt("metodopago_id"),
                rs.getString("numero_mascara"),
                rs.getDate("fecha_expiracion").toLocalDate(),
                rs.getBoolean("habilitado"),
                rs.getInt("tipo_id"),
                rs.getString("tipo_nombre")
        ), usuarioId);
    }

    // Lista los tipos de metodo de pago disponibles.
    public List<TipoMetodoPago> listarTipos() {
        String sql = "SELECT * FROM pedidos.fn_tipos_metodopago()";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TipoMetodoPago(
                rs.getInt("tipo_id"),
                rs.getString("nombre")
        ));
    }

    public void agregar(String numeroTarjeta, LocalDate fechaExpiracion, Integer tipoId, Integer usuarioId) {
        ejecutarProcesarMetodoPago(Map.of(
                "Accion", "agregar",
                "NumeroTar", numeroTarjeta,
                "FechaEx", fechaExpiracion.toString(),
                "TipoId", tipoId,
                "UsuarioId", usuarioId
        ));
    }

    public void actualizar(Integer metodopagoId, Integer usuarioId, String numeroTarjeta,
                           LocalDate fechaExpiracion, Integer tipoId, Boolean habilitado) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Accion", "editar");
        payload.put("MetodoId", metodopagoId);
        payload.put("UsuarioId", usuarioId);
        if (numeroTarjeta != null) payload.put("NumeroTar", numeroTarjeta);
        if (fechaExpiracion != null) payload.put("FechaEx", fechaExpiracion.toString());
        if (tipoId != null) payload.put("TipoId", tipoId);
        if (habilitado != null) payload.put("Habilitado", habilitado);
        ejecutarProcesarMetodoPago(payload);
    }

    // El procedimiento borra fisicamente si puede; si hay FK, deshabilita.
    public void inactivar(Integer metodopagoId, Integer usuarioId) {
        ejecutarProcesarMetodoPago(Map.of(
                "Accion", "eliminar",
                "MetodoId", metodopagoId,
                "UsuarioId", usuarioId
        ));
    }

    public void reactivar(Integer metodopagoId, Integer usuarioId) {
        ejecutarProcesarMetodoPago(Map.of(
                "Accion", "editar",
                "MetodoId", metodopagoId,
                "UsuarioId", usuarioId,
                "Habilitado", true
        ));
    }

    private void ejecutarProcesarMetodoPago(Map<String, Object> item) {
        try {
            String json = objectMapper.writeValueAsString(List.of(item));
            jdbcTemplate.update("CALL pedidos.sp_procesar_metodopago(CAST(? AS jsonb))", json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo construir el JSON para sp_procesar_metodopago", e);
        }
    }
}
