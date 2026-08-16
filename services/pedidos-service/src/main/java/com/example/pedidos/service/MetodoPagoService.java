package com.example.pedidos.service;

import com.example.pedidos.model.MetodoPago;
import com.example.pedidos.model.TipoMetodoPago;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class MetodoPagoService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public MetodoPagoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Lista los metodos de pago enmascarados de un usuario.
    public List<MetodoPago> listarPorUsuario(Integer usuarioId) {
        String sql = """
                SELECT mp.metodopago_id, mp.numero_tarjeta, mp.fecha_expiracion,
                       mp.habilitado, t.tipo_id, t.nombre AS tipo_nombre
                FROM pedidos.metodopago mp
                JOIN pedidos.tipo_metodopago t ON t.tipo_id = mp.tipo_id
                WHERE mp.usuario_id = ?
                ORDER BY mp.metodopago_id DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MetodoPago(
                rs.getInt("metodopago_id"),
                enmascarar(rs.getString("numero_tarjeta")),
                rs.getDate("fecha_expiracion").toLocalDate(),
                rs.getBoolean("habilitado"),
                rs.getInt("tipo_id"),
                rs.getString("tipo_nombre")
        ), usuarioId);
    }

    // Lista los tipos de metodo de pago disponibles.
    public List<TipoMetodoPago> listarTipos() {
        String sql = """
                SELECT tipo_id, nombre
                FROM pedidos.tipo_metodopago
                WHERE habilitado
                ORDER BY nombre
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TipoMetodoPago(
                rs.getInt("tipo_id"),
                rs.getString("nombre")
        ));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void agregar(String numeroTarjeta, LocalDate fechaExpiracion, Integer tipoId, Integer usuarioId) {
        validar(numeroTarjeta, fechaExpiracion);
        Integer existentes = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pedidos.metodopago
                WHERE usuario_id = ? AND numero_tarjeta = ?
                """, Integer.class, usuarioId, numeroTarjeta);
        if (existentes != null && existentes > 0) {
            throw new IllegalArgumentException("La tarjeta ya existe para el usuario");
        }
        jdbcTemplate.update("""
                INSERT INTO pedidos.metodopago
                    (numero_tarjeta, fecha_expiracion, tipo_id, usuario_id, habilitado)
                VALUES (?, ?, ?, ?, true)
                """, numeroTarjeta, fechaExpiracion, tipoId, usuarioId);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void actualizar(Integer metodopagoId, Integer usuarioId, String numeroTarjeta,
                           LocalDate fechaExpiracion, Integer tipoId, Boolean habilitado) {
        if (numeroTarjeta != null || fechaExpiracion != null) {
            validarParcial(numeroTarjeta, fechaExpiracion);
        }
        int actualizados = jdbcTemplate.update("""
                UPDATE pedidos.metodopago
                SET numero_tarjeta = COALESCE(?, numero_tarjeta),
                    fecha_expiracion = COALESCE(?, fecha_expiracion),
                    tipo_id = COALESCE(?, tipo_id),
                    habilitado = COALESCE(?, habilitado)
                WHERE metodopago_id = ? AND usuario_id = ?
                """, numeroTarjeta, fechaExpiracion, tipoId, habilitado, metodopagoId, usuarioId);
        exigirActualizacion(actualizados);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void inactivar(Integer metodopagoId, Integer usuarioId) {
        cambiarEstado(metodopagoId, usuarioId, false);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void reactivar(Integer metodopagoId, Integer usuarioId) {
        cambiarEstado(metodopagoId, usuarioId, true);
    }

    private void cambiarEstado(Integer metodopagoId, Integer usuarioId, boolean habilitado) {
        int actualizados = jdbcTemplate.update("""
                UPDATE pedidos.metodopago SET habilitado = ?
                WHERE metodopago_id = ? AND usuario_id = ?
                """, habilitado, metodopagoId, usuarioId);
        exigirActualizacion(actualizados);
    }

    private static void exigirActualizacion(int actualizados) {
        if (actualizados == 0) {
            throw new IllegalArgumentException("El método de pago no existe para el usuario");
        }
    }

    private static void validar(String numeroTarjeta, LocalDate fechaExpiracion) {
        if (numeroTarjeta == null || !numeroTarjeta.matches("\\d{13,19}")) {
            throw new IllegalArgumentException("El número de tarjeta debe tener entre 13 y 19 dígitos");
        }
        if (fechaExpiracion == null || fechaExpiracion.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de expiración debe ser vigente");
        }
    }

    private static void validarParcial(String numeroTarjeta, LocalDate fechaExpiracion) {
        if (numeroTarjeta != null && !numeroTarjeta.matches("\\d{13,19}")) {
            throw new IllegalArgumentException("El número de tarjeta debe tener entre 13 y 19 dígitos");
        }
        if (fechaExpiracion != null && fechaExpiracion.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de expiración debe ser vigente");
        }
    }

    private static String enmascarar(String numeroTarjeta) {
        if (numeroTarjeta == null || numeroTarjeta.length() <= 4) {
            return numeroTarjeta;
        }
        return "*".repeat(numeroTarjeta.length() - 4)
                + numeroTarjeta.substring(numeroTarjeta.length() - 4);
    }
}
