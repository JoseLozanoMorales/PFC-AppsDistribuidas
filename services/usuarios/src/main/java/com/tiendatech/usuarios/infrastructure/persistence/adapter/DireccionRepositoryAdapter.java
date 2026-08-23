package com.tiendatech.usuarios.infrastructure.persistence.adapter;

import com.tiendatech.usuarios.domain.model.Direccion;
import com.tiendatech.usuarios.domain.port.out.DireccionRepositoryPort;
import com.tiendatech.usuarios.infrastructure.persistence.repository.CiudadRepository;
import com.tiendatech.usuarios.infrastructure.persistence.repository.DireccionRepository;
import com.tiendatech.usuarios.infrastructure.persistence.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DireccionRepositoryAdapter implements DireccionRepositoryPort {
    private final DireccionRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final CiudadRepository ciudadRepository;
    private final JdbcTemplate jdbc;

    @Override
    public List<Direccion> findEnabledByUser(Integer usuarioId) {
        return repository.findByUsuario_UsuarioIdAndHabilitadoTrue(usuarioId).stream().map(this::toDomain).toList();
    }

    @Override
    public Direccion create(Integer usuarioId, Short ciudadId, String calle, String referencia) {
        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"));
        var ciudad = ciudadRepository.findById(ciudadId)
                .orElseThrow(() -> new IllegalArgumentException("Ciudad no existe"));
        var entity = new com.tiendatech.usuarios.infrastructure.persistence.entity.Direccion();
        entity.setUsuario(usuario);
        entity.setCiudad(ciudad);
        entity.setCalle(calle);
        entity.setReferencia(referencia);
        entity.setHabilitado(true);
        return toDomain(repository.save(entity));
    }

    @Override
    public Direccion update(Integer usuarioId, Short direccionId, Short ciudadId, String calle, String referencia) {
        var entity = ownedAddress(usuarioId, direccionId);
        if (calle != null) entity.setCalle(calle);
        if (referencia != null) entity.setReferencia(referencia);
        if (ciudadId != null) entity.setCiudad(ciudadRepository.findById(ciudadId)
                .orElseThrow(() -> new IllegalArgumentException("Ciudad no existe")));
        return toDomain(repository.save(entity));
    }

    @Override
    public void disable(Integer usuarioId, Short direccionId) {
        var entity = ownedAddress(usuarioId, direccionId);
        entity.setHabilitado(false);
        repository.save(entity);
    }

    @Override
    public List<Direccion> findDetailedByUser(Integer usuarioId) {
        String sql = """
                SELECT d.direccion_id,d.ciudad_id,d.calle,d.referencia,c.nombre AS ciudad,
                       p.nombre AS provincia
                  FROM usuarios.direccion d JOIN usuarios.ciudad c ON c.ciudad_id=d.ciudad_id
                  JOIN usuarios.provincia p ON p.provincia_id=c.provincia_id
                 WHERE d.usuario_id=? AND d.habilitado=true ORDER BY d.direccion_id
                """;
        return jdbc.query(sql, (rs, index) -> new Direccion(rs.getShort("direccion_id"), usuarioId,
                rs.getShort("ciudad_id"), rs.getString("ciudad"), rs.getString("provincia"),
                rs.getString("calle"), rs.getString("referencia"), true), usuarioId);
    }

    private com.tiendatech.usuarios.infrastructure.persistence.entity.Direccion ownedAddress(Integer usuarioId, Short id) {
        var entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dirección no existe"));
        if (entity.getUsuario() == null || !entity.getUsuario().getUsuarioId().equals(usuarioId))
            throw new IllegalStateException("La dirección no pertenece al usuario");
        return entity;
    }

    private Direccion toDomain(com.tiendatech.usuarios.infrastructure.persistence.entity.Direccion entity) {
        return new Direccion(entity.getDireccionId(),
                entity.getUsuario() == null ? null : entity.getUsuario().getUsuarioId(),
                entity.getCiudad() == null ? null : entity.getCiudad().getCiudadId(),
                entity.getCiudad() == null ? null : entity.getCiudad().getNombre(), null,
                entity.getCalle(), entity.getReferencia(), !Boolean.FALSE.equals(entity.getHabilitado()));
    }
}
