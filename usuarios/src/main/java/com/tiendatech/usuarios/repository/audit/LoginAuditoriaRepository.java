package com.tiendatech.usuarios.repository.audit;

import com.tiendatech.usuarios.model.UsuarioAuditoria;
import org.springframework.data.jpa.repository.*;

public interface LoginAuditoriaRepository extends JpaRepository<UsuarioAuditoria, Integer> {
}
