package com.tiendatech.usuarios.infrastructure.persistence.repository.audit;

import com.tiendatech.usuarios.infrastructure.persistence.entity.UsuarioAuditoria;
import org.springframework.data.jpa.repository.*;

public interface LoginAuditoriaRepository extends JpaRepository<UsuarioAuditoria, Integer> {
}
