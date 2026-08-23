package com.tiendatech.usuarios.application.service.audit;

import com.tiendatech.usuarios.application.dto.audit.LoginAuditoriaDTO;
import com.tiendatech.usuarios.domain.model.LoginAuditoria;
import com.tiendatech.usuarios.domain.port.out.LoginAuditPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioAuditoriaService {

    private final LoginAuditPort repo;

    @Transactional
    public void registrarLogin(Integer usuarioId) {
        repo.register(usuarioId);
    }

    // ===== nuevos (solo lectura) =====
    @Transactional(readOnly = true)
    public List<String> listarUsuariosVisibles() {
        return repo.visibleUsers();
    }

    @Transactional(readOnly = true)
    public List<LoginAuditoriaDTO> buscarLogins(String usuario, LocalDate desde, LocalDate hasta) {
        return repo.search(
                (usuario == null || usuario.isBlank()) ? "" : usuario.trim(),
                desde, hasta
        ).stream().map(this::toDto).toList();
    }

   // ===== mapeo seguro Object[] -> DTO =====
    private LoginAuditoriaDTO toDto(LoginAuditoria audit) {
        return new LoginAuditoriaDTO(audit.idSesion(), audit.usuarioId(), audit.usuario(), audit.ip(),
                audit.host(), audit.fechaLogin());
    }
}
