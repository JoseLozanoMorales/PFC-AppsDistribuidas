package com.tiendatech.usuarios.infrastructure.persistence.adapter;

import com.tiendatech.usuarios.domain.model.LoginAuditoria;
import com.tiendatech.usuarios.domain.port.out.LoginAuditPort;
import com.tiendatech.usuarios.infrastructure.persistence.repository.audit.UsuarioAuditoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LoginAuditAdapter implements LoginAuditPort {
    private final UsuarioAuditoriaRepository repository;

    @Override public void register(Integer usuarioId) { repository.auditarLogin(usuarioId); }
    @Override public List<String> visibleUsers() { return repository.fnUsuarios(); }

    @Override
    public List<LoginAuditoria> search(String usuario, LocalDate desde, LocalDate hasta) {
        return repository.fnLoginsRaw(usuario, desde, hasta).stream().map(this::toDomain).toList();
    }

    private LoginAuditoria toDomain(Object[] row) {
        return new LoginAuditoria(asInt(row[0]), asInt(row[1]), (String) row[2], (String) row[3],
                (String) row[4], asOffsetDateTime(row[5]));
    }

    private Integer asInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private OffsetDateTime asOffsetDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime offset) return offset;
        if (value instanceof java.sql.Timestamp timestamp)
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        if (value instanceof java.time.LocalDateTime local)
            return local.atOffset(java.time.ZoneOffset.UTC);
        if (value instanceof java.util.Date date)
            return date.toInstant().atOffset(java.time.ZoneOffset.UTC);
        return OffsetDateTime.parse(value.toString());
    }
}
