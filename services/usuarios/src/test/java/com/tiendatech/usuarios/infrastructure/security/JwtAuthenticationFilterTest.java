package com.tiendatech.usuarios.infrastructure.security;

import com.tiendatech.usuarios.domain.model.auth.AccessClaims;
import com.tiendatech.usuarios.domain.model.auth.RefreshClaims;
import com.tiendatech.usuarios.domain.port.out.TokenPort;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JwtAuthenticationFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesValidBearerTokenWithRole() throws Exception {
        TokenPort tokens = new StubTokenPort(new AccessClaims(7, "jeremy", "ADMIN"));
        var filter = new JwtAuthenticationFilter(tokens);
        var request = new MockHttpServletRequest("GET", "/api/usuarios/me");
        request.addHeader("Authorization", "Bearer valid-token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertInstanceOf(AccessClaims.class,
                SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals("ROLE_ADMIN", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        TokenPort tokens = new StubTokenPort(null);
        var filter = new JwtAuthenticationFilter(tokens);
        var request = new MockHttpServletRequest("GET", "/api/usuarios/me");
        request.addHeader("Authorization", "Bearer invalid-token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    private record StubTokenPort(AccessClaims claims) implements TokenPort {
        public String generateAccess(Integer userId, String username, String role, int minutes) {
            throw new UnsupportedOperationException();
        }
        public String generateRefresh(Integer userId, String role, UUID jti, UUID familyId,
                                      Instant absoluteExpiration) {
            throw new UnsupportedOperationException();
        }
        public AccessClaims parseAccess(String token) {
            if (claims == null) throw new MalformedJwtException("invalid");
            return claims;
        }
        public RefreshClaims parseRefresh(String token) {
            throw new UnsupportedOperationException();
        }
    }
}
