package com.tiendatech.productos.infrastructure.client;

import com.tiendatech.productos.domain.VentasPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class VentasClient implements VentasPort {
    private final RestClient client;

    public VentasClient(RestClient.Builder builder,
                        @Value("${ventas.service.url:http://tiendatech-ventas:8086}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public List<Map<String, Object>> masVendidos(int limite) {
        var body = client.get().uri("/internal/ventas/mas-vendidos?limite={limite}", limite)
                .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        return body == null ? List.of() : body;
    }
}
