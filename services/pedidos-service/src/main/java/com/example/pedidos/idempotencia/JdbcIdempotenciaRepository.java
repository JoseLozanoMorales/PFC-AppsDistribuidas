package com.example.pedidos.idempotencia;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Implementacion real contra pedidos.solicitud_idempotente. Solo se registra
 * como bean si pedidos.idempotencia.enabled=true (default false), porque esa
 * tabla NO existe todavia en docs/db/schema.sql -- ver
 * services/pedidos-service/docs/idempotencia.sql para el DDL exacto y lo que
 * falta para activarla. Con la bandera en false esta clase ni se instancia:
 * OrdenService recibe un Optional<IdempotenciaRepository> vacio y el checkout
 * se comporta exactamente igual que antes de esta funcionalidad.
 */
@Repository
@ConditionalOnProperty(prefix = "pedidos.idempotencia", name = "enabled", havingValue = "true")
public class JdbcIdempotenciaRepository implements IdempotenciaRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JdbcIdempotenciaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SolicitudIdempotente> buscarPorUsuarioYClave(Integer usuarioId, String clave) {
        String sql = "SELECT usuario_id, clave, payload_hash, orden_id, creado_en " +
                "FROM pedidos.solicitud_idempotente WHERE usuario_id = ? AND clave = ?";
        List<SolicitudIdempotente> resultado = jdbcTemplate.query(sql, (rs, rowNum) -> new SolicitudIdempotente(
                rs.getInt("usuario_id"),
                rs.getString("clave"),
                rs.getString("payload_hash"),
                rs.getInt("orden_id"),
                rs.getTimestamp("creado_en").toInstant()
        ), usuarioId, clave);
        return resultado.isEmpty() ? Optional.empty() : Optional.of(resultado.get(0));
    }

    @Override
    public void registrar(Integer usuarioId, String clave, String payloadHash, Integer ordenId) {
        String sql = "INSERT INTO pedidos.solicitud_idempotente (usuario_id, clave, payload_hash, orden_id) " +
                "VALUES (?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql, usuarioId, clave, payloadHash, ordenId);
        } catch (DuplicateKeyException ex) {
            throw new ClaveIdempotenciaEnConflictoException(
                    "Ya existe una solicitud registrada con la clave " + clave + " para el usuario " + usuarioId, ex);
        }
    }
}
