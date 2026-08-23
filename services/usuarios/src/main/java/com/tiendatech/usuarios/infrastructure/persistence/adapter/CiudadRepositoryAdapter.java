package com.tiendatech.usuarios.infrastructure.persistence.adapter;

import com.tiendatech.usuarios.domain.model.Ciudad;
import com.tiendatech.usuarios.domain.port.out.CiudadRepositoryPort;
import com.tiendatech.usuarios.infrastructure.persistence.repository.CiudadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CiudadRepositoryAdapter implements CiudadRepositoryPort {
    private final CiudadRepository repository;

    @Override
    public List<Ciudad> findAllByName() {
        return repository.findAll(Sort.by("nombre")).stream()
                .map(entity -> new Ciudad(entity.getCiudadId(), entity.getNombre(), entity.getProvinciaId()))
                .toList();
    }

    @Override public void create(String nombre, Short provinciaId) { repository.agregar(nombre, provinciaId); }
    @Override public void update(Short id, String nombre, Short provinciaId) { repository.editar(id, nombre, provinciaId); }
    @Override public void disable(Short id) { repository.eliminar(id); }
}
