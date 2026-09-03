package com.tiendatech.ventas.infrastructure.experiment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Fallos controlados, desactivados por defecto y permitidos solo en el perfil experimental. */
@Component
public class ExperimentFaultInjector {
    private final boolean enabled;
    private final long delayMillis;
    private final long omissionDelayMillis;

    public ExperimentFaultInjector(@Value("${experiment.fault-injection.enabled:false}") boolean enabled,
                                   @Value("${experiment.fault-delay-ms:5000}") long delayMillis,
                                   @Value("${experiment.omission-delay-ms:9000}") long omissionDelayMillis) {
        this.enabled = enabled;
        this.delayMillis = delayMillis;
        this.omissionDelayMillis = omissionDelayMillis;
    }

    public void apply(String mode) {
        if (!enabled || mode == null || mode.equalsIgnoreCase("none")) return;
        if (mode.equalsIgnoreCase("timing")) {
            sleep(delayMillis);
            return;
        }
        if (mode.equalsIgnoreCase("omission")) {
            sleep(omissionDelayMillis);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "fallo experimental: respuesta omitida");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Failure-Mode debe ser none, omission o timing");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "fallo experimental interrumpido");
        }
    }
}
