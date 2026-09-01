package com.tiendatech.pedidos.infrastructure.client;

import com.tiendatech.pedidos.domain.DireccionInfo;
import com.tiendatech.pedidos.domain.UsuarioInfo;
import com.tiendatech.pedidos.domain.UsuarioPort;
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
public class UsuarioClient implements UsuarioPort {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public UsuarioClient(RestClient.Builder restClientBuilder,
                         @Value("${usuarios.service.base-url}") String usuariosBaseUrl,
                         CircuitBreakerRegistry circuitBreakerRegistry,
                         RetryRegistry retryRegistry,
                         InboundAuthorizationInterceptor authorizationInterceptor) {
        this.restClient = restClientBuilder.baseUrl(usuariosBaseUrl)
                .requestInterceptor(authorizationInterceptor)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("usuarioClient");
        this.retry = retryRegistry.retry("usuarioClient");
    }

    /**
     * usuarios-service expone GET /api/usuarios/{id} para lookup exacto por ID.
     */
    @Override
    public UsuarioInfo obtenerUsuario(Integer usuarioId) {
        ApiEnvelope<UsuarioResponse> envelope = lectura(() -> restClient.get()
                .uri("/api/usuarios/{id}", usuarioId)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiEnvelope<UsuarioResponse>>() { }));
        UsuarioResponse response = envelope == null ? null : envelope.data();
        return response == null ? null : new UsuarioInfo(response.usuarioId(), response.nombre(),
                response.cedula(), response.correo(), response.telefono(), response.usuario(),
                response.rolId(), response.habilitado());
    }

    @Override
    public List<DireccionInfo> obtenerDirecciones(Integer usuarioId) {
        ApiEnvelope<List<DireccionResponse>> envelope = lectura(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/usuarios/{usuarioId}/direcciones")
                        .queryParam("view", "full")
                        .build(usuarioId))
                .retrieve()
                .body(new ParameterizedTypeReference<ApiEnvelope<List<DireccionResponse>>>() {
                }));
        List<DireccionResponse> direcciones = envelope == null ? List.of() : envelope.data();
        return direcciones == null ? List.of() : direcciones.stream()
                .map(d -> new DireccionInfo(d.direccionId(), d.usuarioId(), d.calle(), d.referencia(),
                        d.ciudadId(), d.ciudadNombre(), d.provinciaNombre(), d.habilitado()))
                .toList();
    }

    // Lectura (GET), idempotente por naturaleza: circuit breaker + reintento.
    private <T> T lectura(Supplier<T> operacion) {
        Supplier<T> conCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, operacion);
        Supplier<T> conReintento = Retry.decorateSupplier(retry, conCircuitBreaker);
        return conReintento.get();
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record UsuarioResponse(Integer usuarioId, String nombre, String cedula, String correo,
                                   String telefono, String usuario,
                                   @com.fasterxml.jackson.annotation.JsonProperty("id_rol") Integer rolId,
                                   Boolean habilitado) { }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record DireccionResponse(Integer direccionId, Integer usuarioId, String calle,
                                     String referencia, Integer ciudadId, String ciudadNombre,
                                     String provinciaNombre, Boolean habilitado) { }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiEnvelope<T>(T data) { }
}
