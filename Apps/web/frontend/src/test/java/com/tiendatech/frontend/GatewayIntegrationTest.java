package com.tiendatech.frontend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {
    private static final String SECRET = "TiendaTechDistribuidaJwtSecretKey2026Seguro";
    private static final HttpServer PRODUCTOS = server("/api/productos/10", "{\"servicio\":\"productos\",\"id\":10}");
    private static final HttpServer USUARIOS = server("/api/provincias", "[{\"id\":1,\"nombre\":\"Guayas\"}]");
    private static final HttpServer PEDIDOS = server("/api/ordenes", "{\"servicio\":\"pedidos\",\"creada\":true}");

    @LocalServerPort int gatewayPort;

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("tiendatech.security.jwt.secret", () -> SECRET);
        registry.add("PRODUCTOS_SERVICE_URL", () -> url(PRODUCTOS));
        registry.add("USUARIOS_SERVICE_URL", () -> url(USUARIOS));
        registry.add("PEDIDOS_SERVICE_URL", () -> url(PEDIDOS));
        registry.add("tiendatech.security.jwt.public-read-paths", () -> "/api/productos/**,/api/provincias/**");
    }

    @AfterAll static void stopServers() { PRODUCTOS.stop(0); USUARIOS.stop(0); PEDIDOS.stop(0); }

    @Test void flujoCatalogoAtraviesaGatewayHastaProductos() throws Exception {
        var response = get("/api/productos/10");
        assertEquals(200, response.statusCode()); assertTrue(response.body().contains("productos"));
    }

    @Test void flujoUbicacionesAtraviesaGatewayHastaUsuarios() throws Exception {
        var response = get("/api/provincias");
        assertEquals(200, response.statusCode()); assertTrue(response.body().contains("Guayas"));
    }

    @Test void flujoPedidosAtraviesaGatewayHastaElMicroservicio() throws Exception {
        var response = get("/api/ordenes", token());
        assertEquals(200, response.statusCode()); assertTrue(response.body().contains("creada"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/usuarios/buscar", "/api/movimientos", "/api/carrito/1",
            "/api/ordenes", "/api/proveedores", "/api/ordenes-compra",
            "/api/facturas/1", "/api/armado/recomendar"
    })
    void todaFamiliaProtegidaRechazaSolicitudSinToken(String path) throws Exception {
        assertEquals(401, get(path).statusCode(), path);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(gateway(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(gateway(path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().setSubject("iso-25010").claim("role", "ADMIN")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key, SignatureAlgorithm.HS256).compact();
    }

    private URI gateway(String path) { return URI.create("http://localhost:" + gatewayPort + path); }
    private static String url(HttpServer server) { return "http://localhost:" + server.getAddress().getPort(); }

    private static HttpServer server(String path, String response) {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext(path, exchange -> respond(exchange, response)); server.start(); return server;
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    private static void respond(HttpExchange exchange, String response) throws IOException {
        byte[] bytes=response.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json");
        exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }

}
