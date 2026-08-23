package org.example.infrastructure.client;

import org.example.domain.InventarioPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Llama a inventario-service (POST /api/sp/movimiento-inventario) para registrar las
 * ENTRADAS de stock de una recepcion de mercaderia ya confirmada por
 * sp_registrar_recepcion_json. subtipo_id = 1 = "COMPRA" (confirmado contra
 * inventario.subtipo_movimiento).
 */
@Component
public class InventarioClient implements InventarioPort {

    private static final Logger LOG = LoggerFactory.getLogger(InventarioClient.class);
    private static final int SUBTIPO_COMPRA = 1;

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

    /**
     * @param ordenCompraId        para dejar trazabilidad en "referencia"
     * @param recepcionPorProducto producto_id -> cantidad recibida AHORA (no acumulada)
     * @param costoPorProducto     producto_id -> costo_unitario negociado en la orden (para que
     *                             el kardex de inventario-service recalcule el costo promedio
     *                             ponderado con el costo real de compra, no con el anterior)
     * @param usuario               nombre de usuario para auditoria; puede ser null
     */
    @CircuitBreaker(name = "inventario", fallbackMethod = "registrarEntradasPorRecepcionFallback")
    @Retry(name = "inventario")
    @Override
    public void registrarEntradasPorRecepcion(Integer ordenCompraId,
                                              Map<Integer, Integer> recepcionPorProducto,
                                              Map<Integer, BigDecimal> costoPorProducto,
                                              String usuario) {
        List<Map<String, Object>> items = recepcionPorProducto.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("producto_id", entry.getKey());
                    item.put("subtipo_id", SUBTIPO_COMPRA);
                    item.put("cantidad", entry.getValue());
                    item.put("referencia", "OC-" + ordenCompraId);
                    BigDecimal costo = costoPorProducto == null ? null : costoPorProducto.get(entry.getKey());
                    if (costo != null) item.put("costo_unitario", costo);
                    return item;
                })
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
    private void registrarEntradasPorRecepcionFallback(Integer ordenCompraId,
                                                       Map<Integer, Integer> recepcionPorProducto,
                                                       Map<Integer, BigDecimal> costoPorProducto,
                                                       String usuario, Throwable t) {
        // t es la causa real (timeout, conexion rechazada, 4xx/5xx, circuito abierto, etc.).
        // Sin este log quedaba invisible: el ApiExceptionHandler nunca lo imprime, solo lo
        // devuelve como texto generico al cliente.
        LOG.error("Fallo registrarEntradasPorRecepcion para la orden {} (producto->cantidad={}): {}",
                ordenCompraId, recepcionPorProducto, t.toString(), t);
        throw new IllegalStateException(
                "inventario-service no disponible (circuito abierto o reintentos agotados) para la orden "
                        + ordenCompraId, t);
    }
}
