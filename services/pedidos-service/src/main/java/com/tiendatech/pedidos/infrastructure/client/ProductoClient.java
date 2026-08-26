package com.tiendatech.pedidos.infrastructure.client;

import com.tiendatech.pedidos.domain.ProductoInfo;
import com.tiendatech.pedidos.domain.ProductoPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

@Component
public class ProductoClient implements ProductoPort {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ProductoClient(RestClient.Builder restClientBuilder,
                           @Value("${productos.service.base-url}") String productosBaseUrl,
                           CircuitBreakerRegistry circuitBreakerRegistry,
                           RetryRegistry retryRegistry,
                           InboundAuthorizationInterceptor authorizationInterceptor) {
        this.restClient = restClientBuilder.baseUrl(productosBaseUrl)
                .requestInterceptor(authorizationInterceptor)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("productoClient");
        this.retry = retryRegistry.retry("productoClient");
    }

    /**
     * Combina GET /api/productos (precio + iva_id) con GET /api/sp/ivas (iva_id -> porcentaje)
     * para devolver precio y porcentaje de IVA de un producto.
     */
    @Override
    public ProductoInfo obtenerPrecioEIva(Integer productoId) {
        List<ProductoListItem> productos = lectura(() -> restClient.get()
                .uri("/api/productos?page=0&size=1000")
                .retrieve()
                .body(new ParameterizedTypeReference<List<ProductoListItem>>() {
                }));

        ProductoListItem producto = (productos == null ? List.<ProductoListItem>of() : productos).stream()
                .filter(p -> productoId.equals(p.productoId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Producto " + productoId + " no encontrado en productos-service"));

        List<IvaListItem> ivas = lectura(() -> restClient.get()
                .uri("/api/sp/ivas")
                .retrieve()
                .body(new ParameterizedTypeReference<List<IvaListItem>>() {
                }));

        BigDecimal porcentajeIva = (ivas == null ? List.<IvaListItem>of() : ivas).stream()
                .filter(i -> producto.ivaId().equals(i.ivaId()))
                .map(IvaListItem::porcentaje)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "IVA " + producto.ivaId() + " no encontrado en productos-service"));

        return new ProductoInfo(producto.productoId(), producto.precioUnitario(), producto.ivaId(), porcentajeIva);
    }

    // Lectura (GET), idempotente por naturaleza: circuit breaker + reintento.
    // Se decora solo la llamada HTTP, no la logica de negocio de alrededor
    // (los IllegalArgumentException de "no encontrado" quedan fuera y nunca se
    // reintentan).
    private <T> T lectura(Supplier<T> operacion) {
        Supplier<T> conCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, operacion);
        Supplier<T> conReintento = Retry.decorateSupplier(retry, conCircuitBreaker);
        return conReintento.get();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductoListItem(
            @JsonProperty("producto_id") Integer productoId,
            @JsonProperty("preciounitario") BigDecimal precioUnitario,
            @JsonProperty("iva_id") Integer ivaId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IvaListItem(
            @JsonProperty("iva_id") Integer ivaId,
            @JsonProperty("porcentaje") BigDecimal porcentaje) {
    }
}
