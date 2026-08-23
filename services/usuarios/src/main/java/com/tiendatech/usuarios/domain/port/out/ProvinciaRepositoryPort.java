package com.tiendatech.usuarios.domain.port.out;

import com.tiendatech.usuarios.domain.model.Provincia;
import java.util.List;

public interface ProvinciaRepositoryPort {
    List<Provincia> findAllByName();
    void create(String nombre);
    void update(Long id, String nombre);
    void disable(Long id);
}
