package com.tiendatech.usuarios.domain.port.out;

import com.tiendatech.usuarios.domain.model.LoginAuditoria;
import java.time.LocalDate;
import java.util.List;

public interface LoginAuditPort {
    void register(Integer usuarioId);
    List<String> visibleUsers();
    List<LoginAuditoria> search(String usuario, LocalDate desde, LocalDate hasta);
}
