package org.example.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import org.example.model.FacturaDetalle;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class InventarioClient {

    // subtipo_id  en la BD
    private static final int SUBTIPO_VENTA = 4;

    private final RestClient restClient;

    public InventarioClient(RestClient.Builder restClientBuilder,
                            @Value("${inventario.service.base-url}") String inventarioBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(inventarioBaseUrl).build();
    }

    /** Descuenta stock por cada línea de la factura recién generada. */
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
}