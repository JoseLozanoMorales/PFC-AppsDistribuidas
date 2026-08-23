package com.tiendatech.usuarios.application.service;

import com.tiendatech.usuarios.application.dto.CiudadDTO;
import com.tiendatech.usuarios.domain.port.out.CiudadRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CiudadService {
    private final CiudadRepositoryPort repo;

    @Transactional(readOnly = true)
    public List<CiudadDTO> listar() {
        return repo.findAllByName().stream().map(e -> {
            var d = new CiudadDTO();
            d.setCiudadId(e.id());
            d.setNombre(e.nombre());
            d.setProvinciaId(e.provinciaId());
            return d;
        }).toList();
    }

    @Transactional
    public void crear(CiudadDTO dto) {
        String nombre = dto.getNombre()==null ? "" : dto.getNombre().trim();
        if (nombre.isEmpty()) throw new IllegalArgumentException("El nombre es obligatorio");
        if (dto.getProvinciaId()==null) throw new IllegalArgumentException("ProvinciaId es obligatorio");
        repo.create(nombre, dto.getProvinciaId());
    }

    @Transactional
    public void actualizar(Short id, CiudadDTO dto) {           // <-- Short
        if (dto.getProvinciaId()==null) {
            throw new IllegalArgumentException("ProvinciaId es obligatorio");
        }
        String n = (dto.getNombre()==null || dto.getNombre().trim().isEmpty()) ? null : dto.getNombre().trim();
        repo.update(id, n, dto.getProvinciaId());
    }

    @Transactional
    public void eliminar(Short id) { repo.disable(id); }
}
