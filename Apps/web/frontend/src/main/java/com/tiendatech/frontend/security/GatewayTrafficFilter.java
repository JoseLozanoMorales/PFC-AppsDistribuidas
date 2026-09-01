package com.tiendatech.frontend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Protección local por ventana fija. Varias réplicas requieren un limitador compartido. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayTrafficFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayTrafficFilter.class);
    private final Clock clock;
    private final int limit;
    private final int maxClients;
    private final long windowMillis;
    private final Map<String, Integer> counts = new HashMap<>();
    private long windowStart;

    @Autowired
    public GatewayTrafficFilter(Environment environment) {
        this(environment, Clock.systemUTC());
    }

    GatewayTrafficFilter(Environment environment, Clock clock) {
        this.clock = clock;
        limit = environment.getProperty("GATEWAY_RATE_LIMIT_REQUESTS", Integer.class, 300);
        maxClients = environment.getProperty("GATEWAY_RATE_LIMIT_MAX_CLIENTS", Integer.class, 10000);
        int seconds = environment.getProperty("GATEWAY_RATE_LIMIT_WINDOW_SECONDS", Integer.class, 60);
        if (limit < 1 || maxClients < 1 || seconds < 1) {
            throw new IllegalArgumentException("Gateway rate limits must be positive");
        }
        windowMillis = seconds * 1000L;
        windowStart = clock.millis();
    }

    // Usa el par de transporte; nunca confía en X-Forwarded-For enviado por el cliente.
    synchronized long retryAfter(String peer) {
        long now = clock.millis();
        if (now < windowStart || now - windowStart >= windowMillis) {
            counts.clear();
            windowStart = now;
        }
        int count = counts.getOrDefault(peer, 0);
        if (count >= limit || (count == 0 && counts.size() >= maxClients)) {
            return Math.max(1, (windowMillis - (now - windowStart) + 999) / 1000);
        }
        counts.put(peer, count + 1);
        return 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean failed = false;
        try {
            String path = request.getRequestURI();
            boolean api = path.equals("/api") || path.startsWith("/api/")
                    || path.equals("/auth") || path.startsWith("/auth/");
            long retry = api && !"OPTIONS".equals(request.getMethod())
                    ? retryAfter(request.getRemoteAddr()) : 0;
            if (retry > 0) {
                response.setStatus(429);
                response.setHeader("Retry-After", Long.toString(retry));
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"status\":\"error\",\"data\":null,"
                        + "\"message\":\"Limite de peticiones excedido\",\"timestamp\":\""
                        + Instant.now(clock) + "\"}");
                return;
            }
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException error) {
            failed = true;
            throw error;
        } finally {
            LOG.info("gateway_request timestamp={} method={} path={} origin={} status={}",
                    Instant.now(clock), safe(request.getMethod()), safe(request.getRequestURI()),
                    safe(request.getRemoteAddr()), failed ? 500 : response.getStatus());
        }
    }

    private static String safe(String value) {
        if (value == null) return "unknown";
        return value.substring(0, Math.min(value.length(), 512)).replaceAll("[\\p{Cntrl}\\s]", "_");
    }
}
