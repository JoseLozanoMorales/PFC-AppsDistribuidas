package org.example.infrastructure.config;

import org.example.domain.BusinessMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Adaptador de BusinessMetricsPort sobre Micrometer. Un unico contador
 * "app.business.events" (expuesto como app_business_events_total, ver D6.1)
 * con etiquetas evento/resultado/motivo en vez de un contador por evento: asi
 * Grafana puede agrupar o filtrar por cualquiera de las tres sin tocar este
 * codigo cuando se agregue el siguiente evento de negocio.
 *
 * MeterRegistry siempre esta disponible aqui (a diferencia de
 * IdempotenciaRepository, que depende de una bandera): micrometer-registry-
 * prometheus + actuator son dependencias incondicionales del pom, asi que no
 * hace falta el mismo Optional/ObjectProvider que usa CrdbMetrics.
 */
@Component
public class BusinessMetrics implements BusinessMetricsPort {

    private static final String METRICA = "app.business.events";

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void registrarCheckoutCompletado() {
        registry.counter(METRICA, "evento", "checkout", "resultado", "completado").increment();
    }

    @Override
    public void registrarCheckoutFallido(String motivo) {
        registry.counter(METRICA, "evento", "checkout", "resultado", "fallido", "motivo", motivo).increment();
    }
}
