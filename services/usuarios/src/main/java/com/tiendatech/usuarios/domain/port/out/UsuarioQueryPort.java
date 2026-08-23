package com.tiendatech.usuarios.domain.port.out;

import com.tiendatech.usuarios.domain.model.UsuarioResumen;
import java.util.List;

public interface UsuarioQueryPort {
    List<UsuarioResumen> search(String query, Integer roleId, int limit);
}
