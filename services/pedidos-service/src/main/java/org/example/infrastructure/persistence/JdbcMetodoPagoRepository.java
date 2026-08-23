package org.example.infrastructure.persistence;

import org.example.domain.MetodoPago;
import org.example.domain.MetodoPagoRepository;
import org.example.domain.PageResponse;
import org.example.domain.Paginacion;
import org.example.domain.TipoMetodoPago;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Adaptador JDBC (patron Repository) del puerto domain.MetodoPagoRepository.
 * Toda la logica SQL y las reglas de transaccion/aislamiento se mantienen
 * identicas a la version previa: este refactor es puramente estructural.
 */
@Repository
public class JdbcMetodoPagoRepository implements MetodoPagoRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMetodoPagoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MetodoPago> metodoPagoRowMapper = (rs, rowNum) -> new MetodoPago(
            rs.getInt("metodopago_id"),
            enmascarar(rs.getString("numero_tarjeta")),
            rs.getDate("fecha_expiracion").toLocalDate(),
            rs.getBoolean("habilitado"),
            rs.getInt("tipo_id"),
            rs.getString("tipo_nombre")
    );

    // Lista los metodos de pago enmascarados de un usuario.
    @Override
    public PageResponse<MetodoPago> listarPorUsuario(Integer usuarioId, Paginacion paginacion) {
        String sql = """
                SELECT mp.metodopago_id, mp.numero_tarjeta, mp.fecha_expiracion,
                       mp.habilitado, t.tipo_id, t.nombre AS tipo_nombre
                FROM pedidos.metodopago mp
                JOIN pedidos.tipo_metodopago t ON t.tipo_id = mp.tipo_id
                WHERE mp.usuario_id = ?
                ORDER BY mp.metodopago_id DESC
                LIMIT ? OFFSET ?
                """;
        List<MetodoPago> contenido = jdbcTemplate.query(
                sql, metodoPagoRowMapper, usuarioId, paginacion.size(), paginacion.offset());
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pedidos.metodopago WHERE usuario_id = ?", Long.class, usuarioId);
        return PageResponse.of(contenido, paginacion, total == null ? 0 : total);
    }

    // Usado para construir el Location de POST /api/metodopago y para el GET por id;
    // el filtro por usuario_id en la propia consulta evita exponer metodos de pago ajenos.
    @Override
    public MetodoPago obtenerPorIdYUsuario(Integer metodopagoId, Integer usuarioId) {
        String sql = """
                SELECT mp.metodopago_id, mp.numero_tarjeta, mp.fecha_expiracion,
                       mp.habilitado, t.tipo_id, t.nombre AS tipo_nombre
                FROM pedidos.metodopago mp
                JOIN pedidos.tipo_metodopago t ON t.tipo_id = mp.tipo_id
                WHERE mp.metodopago_id = ? AND mp.usuario_id = ?
                """;
        List<MetodoPago> resultado = jdbcTemplate.query(sql, metodoPagoRowMapper, metodopagoId, usuarioId);
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    // Lista los tipos de metodo de pago disponibles.
    @Override
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

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Integer agregar(String numeroTarjeta, LocalDate fechaExpiracion, Integer tipoId, Integer usuarioId) {
        validar(numeroTarjeta, fechaExpiracion);
        Integer existentes = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pedidos.metodopago
                WHERE usuario_id = ? AND numero_tarjeta = ?
                """, Integer.class, usuarioId, numeroTarjeta);
        if (existentes != null && existentes > 0) {
            throw new IllegalArgumentException("La tarjeta ya existe para el usuario");
        }
        return jdbcTemplate.queryForObject("""
                INSERT INTO pedidos.metodopago
                    (numero_tarjeta, fecha_expiracion, tipo_id, usuario_id, habilitado)
                VALUES (?, ?, ?, ?, true)
                RETURNING metodopago_id
                """, Integer.class, numeroTarjeta, fechaExpiracion, tipoId, usuarioId);
    }

    @Override
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

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void inactivar(Integer metodopagoId, Integer usuarioId) {
        cambiarEstado(metodopagoId, usuarioId, false);
    }

    @Override
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
