package com.tiendatech.usuarios.presentation.support;

import com.tiendatech.usuarios.domain.model.auth.AccessClaims;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserAccessGuard {
    public void requireOwnerOrAdmin(Integer requestedUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccessClaims claims)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT requerido");
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin && !claims.userId().equals(requestedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede acceder a datos de otro usuario");
        }
    }

    public Integer currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccessClaims claims) {
            return claims.userId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT requerido");
    }
}
