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
     * usuarios-service no expone un lookup exacto por id (GET /api/usuarios/{id}).
     * Se usa /buscar-min?q={id} y se filtra el resultado por usuarioId exacto.
     */
    public UsuarioInfo obtenerUsuario(Integer usuarioId) {
        List<UsuarioInfo> resultado = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/usuarios/buscar-min")
                        .queryParam("q", usuarioId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<UsuarioInfo>>() {
                });

        return (resultado == null ? List.<UsuarioInfo>of() : resultado).stream()
                .filter(u -> usuarioId.equals(u.usuarioId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Usuario " + usuarioId + " no encontrado en usuarios-service"));
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
