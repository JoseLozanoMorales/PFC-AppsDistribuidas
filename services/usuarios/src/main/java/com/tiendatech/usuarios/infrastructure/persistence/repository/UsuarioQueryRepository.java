package com.tiendatech.usuarios.infrastructure.persistence.repository;

import com.tiendatech.usuarios.domain.model.UsuarioResumen;
import com.tiendatech.usuarios.domain.port.out.UsuarioQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UsuarioQueryRepository implements UsuarioQueryPort {
    private final JdbcTemplate jdbc;

    @Override
    public List<UsuarioResumen> search(String q, Integer rolId, int limit) {
        String sql = """
                SELECT usuario_id,nombre,cedula,correo,telefono,usuario,rol_id,habilitado
                  FROM usuarios.usuario
                 WHERE (coalesce(?, '')='' OR lower(usuario) LIKE '%'||lower(?)||'%' OR lower(nombre) LIKE '%'||lower(?)||'%')
                   AND (? IS NULL OR rol_id=?) ORDER BY usuario LIMIT ?
                """;
        return jdbc.query(sql, ps -> {
            ps.setString(1, q == null ? "" : q.trim());
            ps.setString(2, q == null ? "" : q.trim()); ps.setString(3, q == null ? "" : q.trim());
            if (rolId == null) { ps.setNull(4, Types.INTEGER); ps.setNull(5, Types.INTEGER); }
            else { ps.setInt(4, rolId); ps.setInt(5, rolId); }
            ps.setInt(6, limit <= 0 ? 20 : Math.min(limit, 50));
        }, (rs, i) -> new UsuarioResumen(
                rs.getInt("usuario_id"),
                rs.getString("nombre"),
                rs.getString("cedula"),
                rs.getString("correo"),
                rs.getString("telefono"),
                rs.getString("usuario"),
                rs.getInt("rol_id"),
                rs.getBoolean("habilitado")
        ));
    }
}
