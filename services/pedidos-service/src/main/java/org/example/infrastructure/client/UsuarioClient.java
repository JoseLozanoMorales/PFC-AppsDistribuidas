package org.example.infrastructure.client;

import org.example.infrastructure.client.dto.DireccionInfo;
import org.example.infrastructure.client.dto.UsuarioInfo;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Supplier;

@Component
public class UsuarioClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public UsuarioClient(RestClient.Builder restClientBuilder,
                         @Value("${usuarios.service.base-url}") String usuariosBaseUrl,
                         CircuitBreakerRegistry circuitBreakerRegistry,
                         RetryRegistry retryRegistry) {
        this.restClient = restClientBuilder.baseUrl(usuariosBaseUrl).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("usuarioClient");
        this.retry = retryRegistry.retry("usuarioClient");
    }

    /**
     * usuarios-service expone GET /api/usuarios/{id} para lookup exacto por ID.
     */
    public UsuarioInfo obtenerUsuario(Integer usuarioId) {
        return lectura(() -> restClient.get()
                .uri("/api/usuarios/{id}", usuarioId)
                .retrieve()
                .body(UsuarioInfo.class));
    }

    public List<DireccionInfo> obtenerDirecciones(Integer usuarioId) {
        List<DireccionInfo> direcciones = lectura(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/usuarios/{usuarioId}/direcciones")
                        .queryParam("view", "full")
                        .build(usuarioId))
                .retrieve()
                .body(new ParameterizedTypeReference<List<DireccionInfo>>() {
                }));
        return direcciones == null ? List.of() : direcciones;
    }

    // Lectura (GET), idempotente por naturaleza: circuit breaker + reintento.
    private <T> T lectura(Supplier<T> operacion) {
        Supplier<T> conCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, operacion);
        Supplier<T> conReintento = Retry.decorateSupplier(retry, conCircuitBreaker);
        return conReintento.get();
    }
}
