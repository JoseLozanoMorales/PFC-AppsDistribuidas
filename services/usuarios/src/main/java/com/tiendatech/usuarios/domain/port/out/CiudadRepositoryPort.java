package com.tiendatech.usuarios.domain.port.out;

import com.tiendatech.usuarios.domain.model.Ciudad;
import java.util.List;

public interface CiudadRepositoryPort {
    List<Ciudad> findAllByName();
    void create(String nombre, Short provinciaId);
    void update(Short id, String nombre, Short provinciaId);
    void disable(Short id);
}
