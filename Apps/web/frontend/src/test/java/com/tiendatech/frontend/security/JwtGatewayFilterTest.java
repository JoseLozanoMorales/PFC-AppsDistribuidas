package com.tiendatech.frontend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtGatewayFilterTest {

    private static final String SECRET = "TiendaTechDistribuidaJwtSecretKey2026Seguro";

    private JwtGatewayFilter filter() {
        return new JwtGatewayFilter(new MockEnvironment()
                .withProperty("tiendatech.security.jwt.secret", SECRET));
    }

    @Test
    void permiteLecturaPublicaDelCatalogoSinToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/productos/10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter().doFilter(request, response, (req, res) -> invoked.set(true));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void protegeEscrituraAunqueCompartaRutaConCatalogoPublico() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/productos/10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter().doFilter(request, response, (req, res) -> invoked.set(true));

        assertFalse(invoked.get());
        assertEquals(401, response.getStatus());
    }

    @Test
    void protegeFacturasSinToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/facturas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter().doFilter(request, response, (req, res) -> invoked.set(true));

        assertFalse(invoked.get());
        assertEquals(401, response.getStatus());
    }

    @Test
    void validaTokenYSobrescribeCabecerasDeIdentidad() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ordenes");
        request.addHeader("Authorization", "Bearer " + token());
        request.addHeader("X-User-Id", "usuario-falsificado");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (req, res) -> {
            invoked.set(true);
            assertEquals("42", ((jakarta.servlet.http.HttpServletRequest) req).getHeader("X-User-Id"));
            assertEquals("jose", ((jakarta.servlet.http.HttpServletRequest) req).getHeader("X-Usuario"));
            assertEquals("ADMIN", ((jakarta.servlet.http.HttpServletRequest) req).getHeader("X-User-Role"));
        };

        filter().doFilter(request, response, chain);

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rechazaTokenCaducado() throws Exception {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder().setSubject("42")
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key, SignatureAlgorithm.HS256).compact();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ordenes");
        request.addHeader("Authorization", "Bearer " + expired);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request, response, (req, res) -> {
            throw new AssertionError("No debe reenviar un token caducado");
        });
        assertEquals(401, response.getStatus());
    }

    @Test
    void rechazaTokenMalformado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ordenes");
        request.addHeader("Authorization", "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request, response, (req, res) -> {
            throw new AssertionError("No debe reenviar un token invalido");
        });
        assertEquals(401, response.getStatus());
    }

    private String token() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("42")
                .claim("username", "jose")
                .claim("role", "ADMIN")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
