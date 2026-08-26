package com.tiendatech.ventas.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import com.tiendatech.ventas.domain.FacturaDetalle;
import com.tiendatech.ventas.domain.InventarioPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class InventarioClient implements InventarioPort {

    // subtipo_id  en la BD
    private static final int SUBTIPO_VENTA = 4;

    private final RestClient restClient;

    public InventarioClient(RestClient.Builder restClientBuilder,
                            @Value("${inventario.service.base-url}") String inventarioBaseUrl,
                            @Value("${inventario.service.connect-timeout-ms:3000}") int connectTimeoutMs,
                            @Value("${inventario.service.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = restClientBuilder
                .baseUrl(inventarioBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** Descuenta stock por cada línea de la factura recién generada. */
    @CircuitBreaker(name = "inventario", fallbackMethod = "registrarSalidasPorFacturaFallback")
    @Retry(name = "inventario")
    @Override
    public void registrarSalidasPorFactura(Integer facturaId, List<FacturaDetalle> detalle, String usuario) {
        List<java.util.Map<String, Object>> items = detalle.stream()
                .map(d -> java.util.Map.<String, Object>of(
                        "producto_id", d.getProductoId(),
                        "subtipo_id", SUBTIPO_VENTA,
                        "cantidad", d.getCantidad(),
                        "fecha", LocalDate.now().toString(),
                        "referencia", "FAC-" + facturaId
                ))
                .toList();

        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/sp/movimiento-inventario")
                        .queryParamIfPresent("usuario", Optional.ofNullable(usuario))
                        .build())
                .body(items)
                .retrieve()
                .toBodilessEntity();
    }

    /** Se ejecuta cuando el circuito está abierto o se agotaron los reintentos. */
    private void registrarSalidasPorFacturaFallback(Integer facturaId, List<FacturaDetalle> detalle,
                                                    String usuario, Throwable t) {
        throw new IllegalStateException(
                "inventario-service no disponible (circuito abierto o reintentos agotados) para la factura "
                        + facturaId, t);
    }
}
