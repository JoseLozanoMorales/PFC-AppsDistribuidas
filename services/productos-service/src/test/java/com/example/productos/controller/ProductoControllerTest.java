package com.example.productos.controller;

import com.example.productos.dto.ProductoCreadoResponse;
import com.example.productos.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductoControllerTest {
    @Test
    void crearProductoReturnsCreatedWithLocationAndTypedBody() throws Exception {
        ProductoService service = mock(ProductoService.class);
        when(service.crear(eq(2), any(), eq("jose"))).thenReturn(42L);
        ProductoController controller = new ProductoController(service);

        ResponseEntity<ProductoCreadoResponse> response = controller.crearProducto(
                Map.of("categoria_id", 2, "nombre", "Producto de prueba"),
                "jose");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/api/productos/42"));
        assertThat(response.getBody()).isEqualTo(new ProductoCreadoResponse(true, 42L));
    }
}
