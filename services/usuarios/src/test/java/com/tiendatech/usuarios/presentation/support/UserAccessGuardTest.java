package com.tiendatech.usuarios.presentation.support;

import com.tiendatech.usuarios.domain.model.auth.AccessClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserAccessGuardTest {
    private final UserAccessGuard guard = new UserAccessGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanAccessOwnResource() {
        authenticate(5, "CLIENTE");
        assertDoesNotThrow(() -> guard.requireOwnerOrAdmin(5));
        assertEquals(5, guard.currentUserId());
    }

    @Test
    void clientCannotAccessAnotherUsersResource() {
        authenticate(5, "CLIENTE");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> guard.requireOwnerOrAdmin(9)
        );
        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void adminCanAccessAnotherUsersResource() {
        authenticate(1, "ADMIN");
        assertDoesNotThrow(() -> guard.requireOwnerOrAdmin(9));
    }

    private void authenticate(int userId, String role) {
        var claims = new AccessClaims(userId, "user" + userId, role);
        var authentication = new UsernamePasswordAuthenticationToken(
                claims,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
