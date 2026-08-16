package com.example.pedidos.client;

import com.example.pedidos.client.dto.DireccionInfo;
import com.example.pedidos.client.dto.UsuarioInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class UsuarioClient {

    private final RestClient restClient;

    public UsuarioClient(RestClient.Builder restClientBuilder,
                         @Value("${usuarios.service.base-url}") String usuariosBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(usuariosBaseUrl).build();
    }

    /**
     * usuarios-service expone GET /api/usuarios/{id} para lookup exacto por ID.
     */
    public UsuarioInfo obtenerUsuario(Integer usuarioId) {
        return restClient.get()
                .uri("/api/usuarios/{id}", usuarioId)
                .retrieve()
                .body(UsuarioInfo.class);
    }

    public List<DireccionInfo> obtenerDirecciones(Integer usuarioId) {
        List<DireccionInfo> direcciones = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/usuarios/{usuarioId}/direcciones")
                        .queryParam("view", "full")
                        .build(usuarioId))
                .retrieve()
                .body(new ParameterizedTypeReference<List<DireccionInfo>>() {
                });
        return direcciones == null ? List.of() : direcciones;
    }
}