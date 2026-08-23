package com.tiendatech.usuarios.infrastructure.persistence.adapter;

import com.tiendatech.usuarios.domain.model.Provincia;
import com.tiendatech.usuarios.domain.port.out.ProvinciaRepositoryPort;
import com.tiendatech.usuarios.infrastructure.persistence.repository.ProvinciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProvinciaRepositoryAdapter implements ProvinciaRepositoryPort {
    private final ProvinciaRepository repository;

    @Override
    public List<Provincia> findAllByName() {
        return repository.findAll(Sort.by("nombre")).stream()
                .map(entity -> new Provincia(entity.getProvinciaId(), entity.getNombre()))
                .toList();
    }

    @Override public void create(String nombre) { repository.agregar(nombre); }
    @Override public void update(Long id, String nombre) { repository.editar(id, nombre); }
    @Override public void disable(Long id) { repository.eliminar(id); }
}
