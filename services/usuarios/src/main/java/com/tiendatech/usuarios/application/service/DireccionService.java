package com.tiendatech.usuarios.application.service;

import com.tiendatech.usuarios.application.dto.DireccionDTO;
import com.tiendatech.usuarios.domain.model.Direccion;
import com.tiendatech.usuarios.domain.port.out.DireccionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DireccionService {
    private final DireccionRepositoryPort repository;

    @Transactional(readOnly = true)
    public List<DireccionDTO> listar(Integer usuarioId) {
        return repository.findEnabledByUser(usuarioId).stream().map(this::toDto).toList();
    }

    @Transactional
    public DireccionDTO crear(Integer usuarioId, DireccionDTO dto) {
        if (dto.getCiudadId() == null) throw new IllegalArgumentException("ciudadId es obligatorio");
        String calle = dto.getCalle() == null ? "" : dto.getCalle().trim();
        if (calle.isEmpty()) throw new IllegalArgumentException("calle es obligatoria");
        String referencia = dto.getReferencia() == null ? null : dto.getReferencia().trim();
        return toDto(repository.create(usuarioId, dto.getCiudadId(), calle, referencia));
    }

    @Transactional
    public DireccionDTO actualizar(Integer usuarioId, Short direccionId, DireccionDTO dto) {
        String calle = dto.getCalle() == null ? null : dto.getCalle().trim();
        String referencia = dto.getReferencia() == null ? null : dto.getReferencia().trim();
        return toDto(repository.update(usuarioId, direccionId, dto.getCiudadId(), calle, referencia));
    }

    @Transactional
    public void eliminar(Integer usuarioId, Short direccionId) {
        repository.disable(usuarioId, direccionId);
    }

    @Transactional(readOnly = true)
    public List<DireccionDTO> listarDetallado(Integer usuarioId) {
        return repository.findDetailedByUser(usuarioId).stream().map(this::toDto).toList();
    }

    private DireccionDTO toDto(Direccion direccion) {
        DireccionDTO dto = new DireccionDTO();
        dto.setDireccionId(direccion.id());
        dto.setUsuarioId(direccion.usuarioId());
        dto.setCiudadId(direccion.ciudadId());
        dto.setCiudadNombre(direccion.ciudadNombre());
        dto.setProvinciaNombre(direccion.provinciaNombre());
        dto.setCalle(direccion.calle());
        dto.setReferencia(direccion.referencia());
        dto.setHabilitado(direccion.habilitada());
        return dto;
    }
}
