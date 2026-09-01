package com.tiendatech.frontend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GatewayTrafficFilterTest {
    private final MockEnvironment env = new MockEnvironment()
            .withProperty("GATEWAY_RATE_LIMIT_REQUESTS", "2")
            .withProperty("GATEWAY_RATE_LIMIT_WINDOW_SECONDS", "60")
            .withProperty("GATEWAY_RATE_LIMIT_MAX_CLIENTS", "2");

    @Test void limitaYReiniciaSinEsperar() {
        MutableClock clock = new MutableClock();
        GatewayTrafficFilter filter = new GatewayTrafficFilter(env, clock);
        assertEquals(0, filter.retryAfter("a"));
        assertEquals(0, filter.retryAfter("a"));
        assertEquals(60, filter.retryAfter("a"));
        clock.now = 60_000;
        assertEquals(0, filter.retryAfter("a"));
    }

    @Test void limitaMemoriaYSeparaClientes() {
        GatewayTrafficFilter filter = new GatewayTrafficFilter(env, new MutableClock());
        assertEquals(0, filter.retryAfter("a"));
        assertEquals(0, filter.retryAfter("b"));
        assertEquals(60, filter.retryAfter("c"));
        assertEquals(0, filter.retryAfter("b"));
    }

    @Test void responde429UniformeEIgnoraForwardedForNoConfiable() throws Exception {
        GatewayTrafficFilter filter = new GatewayTrafficFilter(env, new MutableClock());
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "different-" + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean called = new AtomicBoolean();
            filter.doFilter(request, response, (req, res) -> called.set(true));
            assertEquals(i < 2, called.get());
            assertEquals(i < 2 ? 200 : 429, response.getStatus());
            if (i == 2) {
                assertEquals("60", response.getHeader("Retry-After"));
                assertTrue(response.getContentAsString().contains("\"data\":null"));
                assertTrue(response.getContentAsString().contains("\"timestamp\":"));
            }
        }
    }

    @Test void opcionesEInfraestructuraNoConsumenCupo() throws Exception {
        GatewayTrafficFilter filter = new GatewayTrafficFilter(env, new MutableClock());
        for (String path : new String[]{"/assets/main.js", "/actuator/health", "/api/productos"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        }
        assertEquals(0, filter.retryAfter("127.0.0.1"));
    }

    @Test void rechazaConfiguracionInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new GatewayTrafficFilter(
                env.withProperty("GATEWAY_RATE_LIMIT_REQUESTS", "0")));
    }

    @Test void registraSinQueryNiCredenciales() throws Exception {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(GatewayTrafficFilter.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ordenes");
            request.setQueryString("token=private-query");
            request.addHeader("Authorization", "private-header");
            new GatewayTrafficFilter(env, new MutableClock()).doFilter(request,
                    new MockHttpServletResponse(), (req, res) ->
                            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401));
            String log = appender.list.get(0).getFormattedMessage();
            assertTrue(log.contains("method=POST path=/api/ordenes origin=127.0.0.1 status=401"));
            assertFalse(log.contains("private-query"));
            assertFalse(log.contains("private-header"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static class MutableClock extends Clock {
        long now;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
}
