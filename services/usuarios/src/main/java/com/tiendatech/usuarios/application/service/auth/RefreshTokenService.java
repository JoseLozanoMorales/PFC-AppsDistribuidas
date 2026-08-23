package com.tiendatech.usuarios.application.service.auth;

import com.tiendatech.usuarios.domain.model.auth.RefreshSession;
import com.tiendatech.usuarios.domain.port.out.RefreshSessionRepository;
import com.tiendatech.usuarios.domain.port.out.TokenPort;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshSessionRepository repo;
    private final TokenPort jwt;

    @Value("${auth.access.minutes}")   private int accessMinutes;
    @Value("${auth.absolute.hours}")   private int absoluteHours;
    @Value("${auth.idle.ADMIN}")      private int idleAdmin;
    @Value("${auth.idle.TRABAJADOR}") private int idleTrab;
    @Value("${auth.idle.CLIENTE}")    private int idleCli;

    private int idleForRole(String role) {
        return switch (role) {
            case "ADMIN" -> idleAdmin;
            case "TRABAJADOR" -> idleTrab;
            default -> idleCli;
        };
    }

    public record Tokens(String access){}

    public record IssueResult(String access, String refreshJwt, UUID jti, UUID family, Instant absExp){}

    public IssueResult issueOnLogin(Integer userId, String username, String role){
        var now   = Instant.now();
        var abs   = now.plusSeconds(absoluteHours*3600L);
        var jti   = UUID.randomUUID();
        var fam   = UUID.randomUUID();

        var access = jwt.generateAccess(userId, username, role, accessMinutes);
        var refreshJwt = jwt.generateRefresh(userId, role, jti, fam, abs);

        repo.save(new RefreshSession(jti, userId, role, fam, now, now, abs, false));

        return new IssueResult(access, refreshJwt, jti, fam, abs);
    }

    public record KeepalivePayload(long remainingIdleSeconds, long remainingAbsoluteSeconds){}

    @Transactional
    public KeepaliveResponse refresh(String refreshJwt){
        var claims = jwt.parseRefresh(refreshJwt);
        var jti = claims.jti();
        var role = claims.role();
        var userId = claims.userId();
        var family = claims.familyId();

        var rt = repo.findActive(jti)
                .orElseThrow(() -> new RuntimeException("refresh_revoked"));

        var now = java.time.Instant.now();
        if (now.isAfter(rt.absoluteExpiration())) throw new RuntimeException("session_absolute_expired");

        int idleMin = idleForRole(role);     // <<< AQUÍ
        long remainingIdle = -1;
        if (idleMin > 0){
            long secs = idleMin*60L - (now.getEpochSecond() - rt.lastSeen().getEpochSecond());
            if (secs <= 0) throw new RuntimeException("idle_timeout");
            remainingIdle = secs;
        }

        long remainingAbs = Math.max(0, rt.absoluteExpiration().getEpochSecond() - now.getEpochSecond());

        // Rotación: revoco el viejo y creo uno nuevo en la misma familia
        repo.save(rt.revoke());

        var newJti = UUID.randomUUID();
        repo.save(new RefreshSession(newJti, userId, role, family, now, now,
                rt.absoluteExpiration(), false));

        var access = jwt.generateAccess(userId, null, role, accessMinutes);
        var newRefreshJwt = jwt.generateRefresh(userId, role, newJti, family, rt.absoluteExpiration());

        return new KeepaliveResponse(access, newRefreshJwt, new KeepalivePayload(remainingIdle, remainingAbs));
    }

    public record KeepaliveResponse(String access, String refreshJwt, KeepalivePayload meta){}

    @Transactional
    public void logoutFamily(UUID family){ repo.revokeFamily(family); }
}
