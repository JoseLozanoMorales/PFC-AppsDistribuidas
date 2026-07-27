package com.example.pedidos.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class FacturaClient {

    private final RestClient restClient;

    public FacturaClient(RestClient.Builder restClientBuilder,
                         @Value("${ventas.service.base-url}") String ventasBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(ventasBaseUrl).build();
    }

    public Integer generarFactura(Integer ordenId) {
        Map<String, Integer> resp = restClient.post()
                .uri("/api/facturas")
                .body(Map.of("ordenId", ordenId))
                .retrieve()
                .body(Map.class);
        return (Integer) resp.get("facturaId");
    }
}