package org.example.infrastructure.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Supplier;

@Component
public class FacturaClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public FacturaClient(RestClient.Builder restClientBuilder,
                         @Value("${ventas.service.base-url}") String ventasBaseUrl,
                         CircuitBreakerRegistry circuitBreakerRegistry) {
        this.restClient = restClientBuilder.baseUrl(ventasBaseUrl).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("facturaClient");
    }

    // Escritura (crea una factura en ventas-service): solo circuit breaker, SIN
    // reintento. Este servicio no controla ventas-service y no puede demostrar
    // que POST /api/facturas sea idempotente; reintentar tras un timeout podria
    // duplicar la factura de la misma orden. El llamador (OrdenService) ya
    // convierte cualquier fallo aqui en un mensaje explicito: la orden quedo
    // creada pero la facturacion fallo, en vez de fingir exito o reintentar solo.
    public Integer generarFactura(Integer ordenId) {
        Supplier<Map> llamada = () -> restClient.post()
                .uri("/api/facturas")
                .body(Map.of("ordenId", ordenId))
                .retrieve()
                .body(Map.class);
        Map resp = CircuitBreaker.decorateSupplier(circuitBreaker, llamada).get();
        return (Integer) resp.get("facturaId");
    }
}
