package org.example.infrastructure.persistence;

import org.example.domain.FacturaOutboxStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FacturaOutboxRepository implements FacturaOutboxStore {

    private final JdbcTemplate jdbcTemplate;

    public FacturaOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** IDs de facturas con evento pendiente, listas para reintentar. */
    @Override
    public List<Integer> facturasPendientes(int maxIntentos, int limite) {
        return jdbcTemplate.query("""
                SELECT factura_id
                FROM ventas.factura_outbox
                WHERE estado = 'PENDIENTE' AND intentos < ?
                ORDER BY creado_en
                LIMIT ?
                """, (rs, rowNum) -> rs.getInt("factura_id"), maxIntentos, limite);
    }

    /** Se llama solo después de que inventario confirmó el descuento de stock. */
    @Override
    public void marcarProcesado(Integer facturaId) {
        jdbcTemplate.update("""
                UPDATE ventas.factura_outbox
                SET estado = 'PROCESADO', procesado_en = now()
                WHERE factura_id = ?
                """, facturaId);
    }

    /** Incrementa el contador de intentos; pasa a FALLIDO si se agota el máximo. */
    @Override
    public void registrarFallo(Integer facturaId, String error, int maxIntentos) {
        jdbcTemplate.update("""
                UPDATE ventas.factura_outbox
                SET intentos = intentos + 1,
                    ultimo_error = ?,
                    estado = CASE WHEN intentos + 1 >= ? THEN 'FALLIDO' ELSE 'PENDIENTE' END
                WHERE factura_id = ?
                """, error, maxIntentos, facturaId);
    }
}
