package org.example.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
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
public class InventarioClient {

    private static final int SUBTIPO_COMPRA = 1;

    private final RestClient restClient;

    public InventarioClient(RestClient.Builder restClientBuilder,
                            @Value("${inventario.service.base-url}") String inventarioBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(inventarioBaseUrl).build();
    }

    /**
     * @param ordenCompraId        para dejar trazabilidad en "referencia"
     * @param recepcionPorProducto producto_id -> cantidad recibida AHORA (no acumulada)
     * @param usuario               nombre de usuario para auditoria; puede ser null
     */
    public void registrarEntradasPorRecepcion(Integer ordenCompraId,
                                              Map<Integer, Integer> recepcionPorProducto,
                                              String usuario) {
        List<Map<String, Object>> items = recepcionPorProducto.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "producto_id", entry.getKey(),
                        "subtipo_id", SUBTIPO_COMPRA,
                        "cantidad", entry.getValue(),
                        "fecha", LocalDate.now().toString(),
                        "referencia", "OC-" + ordenCompraId
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
}
