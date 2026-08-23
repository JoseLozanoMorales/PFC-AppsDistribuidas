package com.tiendatech.usuarios.infrastructure.persistence.entity.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity @Table(name="auth_refresh_tokens")
@Getter @Setter @NoArgsConstructor
public class RefreshToken {
    @Id @Column(columnDefinition="uuid") private UUID jti;
    private Integer userId;
    private String role;
    @Column(columnDefinition="uuid") private UUID familyId;
    private Instant issuedAt;
    private Instant lastSeen;
    private Instant absoluteExp;
    private boolean revoked;
}
