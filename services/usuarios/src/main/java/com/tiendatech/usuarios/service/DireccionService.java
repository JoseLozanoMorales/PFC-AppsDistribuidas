package com.tiendatech.usuarios.service;

import com.tiendatech.usuarios.dto.DireccionDTO;
import com.tiendatech.usuarios.model.Ciudad;
import com.tiendatech.usuarios.model.Direccion;
import com.tiendatech.usuarios.model.Usuario;
import com.tiendatech.usuarios.repository.CiudadRepository;
import com.tiendatech.usuarios.repository.DireccionRepository;
import com.tiendatech.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DireccionService {

    private final DireccionRepository repo;
    private final UsuarioRepository usuarioRepo;
    private final CiudadRepository ciudadRepo;

    @Transactional(readOnly = true)
    public List<DireccionDTO> listar(Integer usuarioId){
        return repo.findByUsuario_UsuarioIdAndHabilitadoTrue(usuarioId)
                .stream().map(this::toDTO).toList();
    }

    private final JdbcTemplate jdbc;

    @Transactional
    public DireccionDTO crear(Integer usuarioId, DireccionDTO dto){
        if (dto.getCiudadId()==null) throw new IllegalArgumentException("ciudadId es obligatorio");
        String calle = dto.getCalle()==null ? "" : dto.getCalle().trim();
        if (calle.isEmpty()) throw new IllegalArgumentException("calle es obligatoria");

        Usuario u = usuarioRepo.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"));

        Ciudad c = ciudadRepo.findById(dto.getCiudadId())
                .orElseThrow(() -> new IllegalArgumentException("Ciudad no existe"));

        Direccion d = new Direccion();
        d.setUsuario(u);
        d.setCiudad(c);
        d.setCalle(calle);
        d.setReferencia(dto.getReferencia()==null ? null : dto.getReferencia().trim());
        d.setHabilitado(true);

        return toDTO(repo.save(d));
    }

    @Transactional
    public DireccionDTO actualizar(Integer usuarioId, Short direccionId, DireccionDTO dto){ // <- Short
        Direccion d = repo.findById(direccionId)
                .orElseThrow(() -> new IllegalArgumentException("Dirección no existe"));

        if (d.getUsuario()==null || !d.getUsuario().getUsuarioId().equals(usuarioId))
            throw new IllegalStateException("La dirección no pertenece al usuario");

        if (dto.getCalle()!=null)      d.setCalle(dto.getCalle().trim());
        if (dto.getReferencia()!=null) d.setReferencia(dto.getReferencia().trim());

        if (dto.getCiudadId()!=null){
            Ciudad c = ciudadRepo.findById(dto.getCiudadId())
                    .orElseThrow(() -> new IllegalArgumentException("Ciudad no existe"));
            d.setCiudad(c);
        }
        return toDTO(repo.save(d));
    }

    @Transactional
    public void eliminar(Integer usuarioId, Short direccionId){ // <- Short
        Direccion d = repo.findById(direccionId)
                .orElseThrow(() -> new IllegalArgumentException("Dirección no existe"));
        if (d.getUsuario()==null || !d.getUsuario().getUsuarioId().equals(usuarioId))
            throw new IllegalStateException("La dirección no pertenece al usuario");

        d.setHabilitado(false); // soft-delete
        repo.save(d);
    }

    private DireccionDTO toDTO(Direccion d){
        DireccionDTO dto = new DireccionDTO();
        dto.setDireccionId(d.getDireccionId()); // Short
        dto.setUsuarioId(
                d.getUsuario() != null ? d.getUsuario().getUsuarioId() : null
        ); // Integer
        dto.setCiudadId(
                d.getCiudad() != null ? d.getCiudad().getCiudadId() : null
        ); // Short
        dto.setCiudadNombre(
                d.getCiudad() != null ? d.getCiudad().getNombre() : null
        );
        // si no manejas provincia en Ciudad, deja null
        dto.setProvinciaNombre(null);

        dto.setCalle(d.getCalle());
        dto.setReferencia(d.getReferencia());
        dto.setHabilitado(
                d.getHabilitado() != null ? d.getHabilitado() : Boolean.TRUE
        );
        return dto;
    }
    @Transactional(readOnly = true)
    public List<DireccionDTO> listarDetallado(Integer usuarioId){
        final String sql = """
                SELECT d.direccion_id,d.calle,d.referencia,c.nombre AS nombre_de_ciudad,
                       p.nombre AS nombre_de_provincia
                  FROM usuarios.direccion d JOIN usuarios.ciudad c ON c.ciudad_id=d.ciudad_id
                  JOIN usuarios.provincia p ON p.provincia_id=c.provincia_id
                 WHERE d.usuario_id=? AND d.habilitado=true ORDER BY d.direccion_id
                """;
        return jdbc.query(sql, (rs, i) -> {
            DireccionDTO dto = new DireccionDTO();
            Number n = (Number) rs.getObject("direccion_id");
            dto.setDireccionId(n == null ? null : n.shortValue());  // ← Short, como en tu DTO
            dto.setUsuarioId(usuarioId);
            dto.setCalle(rs.getString("calle"));
            dto.setReferencia(rs.getString("referencia"));
            dto.setCiudadNombre(rs.getString("nombre_de_ciudad"));
            dto.setProvinciaNombre(rs.getString("nombre_de_provincia"));
            dto.setHabilitado(true);
            // Nota: tu función no devuelve ciudad_id; si lo necesitas, amplíala para incluirlo.
            // dto.setCiudadId( ... );
            return dto;
        }, usuarioId);
    }

}
