package com.tiendatech.frontend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.web.server.ResponseStatusException;

/** Vista operacional de solo lectura consumida por el panel administrativo. */
@RestController
@RequestMapping("/api/admin/system")
public class SystemObservabilityController {
    private final List<Target> targets;
    private final String coordination;

    public SystemObservabilityController(
            @Value("${tiendatech.services.usuarios:http://localhost:8085}") String usuarios,
            @Value("${tiendatech.services.pedidos:http://localhost:8083}") String pedidos,
            @Value("${tiendatech.services.inventario:http://localhost:8082}") String inventario,
            @Value("${tiendatech.services.ventas:http://localhost:8086}") String ventas,
            @Value("${tiendatech.services.crdb:http://localhost:8088}") String crdb,
            @Value("${COORD:2pc}") String coordination) {
        this.targets = List.of(new Target("gateway", null), new Target("usuarios", usuarios + "/health"),
                new Target("pedidos", pedidos + "/health"), new Target("inventario", inventario + "/health"),
                new Target("facturacion", ventas + "/health"),
                new Target("cockroachdb", crdb + "/health?ready=1"));
        this.coordination = "saga".equalsIgnoreCase(coordination == null ? "" : coordination.trim()) ? "saga" : "2pc";
    }

    @GetMapping
    public SystemSnapshot status(@RequestHeader("X-User-Role") String role) {
        String normalizedRole = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if (!normalizedRole.equals("admin") && !normalizedRole.equals("administrador") && !normalizedRole.equals("1")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requiere rol administrador");
        }
        Instant checkedAt = Instant.now();
        return new SystemSnapshot(checkedAt, coordination, "COORD (entorno del gateway)",
                targets.parallelStream().map(target -> probe(target, checkedAt)).toList());
    }

    private static ServiceHealth probe(Target target, Instant checkedAt) {
        if (target.url() == null) return new ServiceHealth(target.name(), "UP", 0, checkedAt, null);
        long started = System.nanoTime();
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(target.url()).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            int code = connection.getResponseCode();
            connection.disconnect();
            return new ServiceHealth(target.name(), code >= 200 && code < 400 ? "UP" : "DOWN",
                    elapsedMillis(started), checkedAt, code >= 400 ? "HTTP " + code : null);
        } catch (Exception exception) {
            return new ServiceHealth(target.name(), "DOWN", elapsedMillis(started), checkedAt,
                    exception.getClass().getSimpleName());
        }
    }

    private static long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private record Target(String name, String url) {}
    public record ServiceHealth(String service, String status, long latencyMs, Instant checkedAt, String detail) {}
    public record SystemSnapshot(Instant checkedAt, String coordination, String coordinationSource,
                                 List<ServiceHealth> services) {}
}
