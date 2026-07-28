package com.example.pedidos.service;

import com.example.pedidos.model.MetodoPago;
import com.example.pedidos.model.TipoMetodoPago;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MetodoPagoService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public MetodoPagoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Lista los métodos de pago (enmascarados) de un usuario
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

    // Lista los tipos de método de pago disponibles (Visa, Mastercard, etc.)
    public List<TipoMetodoPago> listarTipos() {
        String sql = "SELECT * FROM pedidos.fn_tipos_metodopago()";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TipoMetodoPago(
                rs.getInt("tipo_id"),
                rs.getString("nombre")
        ));
    }

    // Agrega un nuevo método de pago (valida Luhn, duplicados, etc. dentro del procedure)
    public void agregar(String numeroTarjeta, LocalDate fechaExpiracion, Integer tipoId, Integer usuarioId) {
        String sql = "CALL pedidos.sp_agregar_metodo_pago(?, ?, ?, ?)";
        jdbcTemplate.update(sql, numeroTarjeta, fechaExpiracion, tipoId, usuarioId);
    }

    // Actualiza datos de un método de pago existente (todos los campos opcionales excepto los IDs)
    public void actualizar(Integer metodopagoId, Integer usuarioId, String numeroTarjeta,
                           LocalDate fechaExpiracion, Integer tipoId, Boolean habilitado) {
        String sql = "CALL pedidos.sp_metodopago_actualizar(?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, metodopagoId, usuarioId, numeroTarjeta, fechaExpiracion, tipoId, habilitado);
    }

    // Inactiva (soft delete) un método de pago
    public void inactivar(Integer metodopagoId, Integer usuarioId) {
        String sql = "CALL pedidos.sp_metodopago_inactivar(?, ?)";
        jdbcTemplate.update(sql, metodopagoId, usuarioId);
    }

    // Reactiva un método de pago previamente inactivado
    public void reactivar(Integer metodopagoId, Integer usuarioId) {
        String sql = "CALL pedidos.sp_metodopago_reactivar(?, ?)";
        jdbcTemplate.update(sql, metodopagoId, usuarioId);
    }
}