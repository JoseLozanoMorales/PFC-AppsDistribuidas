package org.example.domain;

import java.util.List;

public interface UsuarioPort {
    UsuarioInfo obtenerUsuario(Integer usuarioId);
    List<DireccionInfo> obtenerDirecciones(Integer usuarioId);
}
