package com.tiendatech.productos.presentation;

import com.tiendatech.productos.presentation.dto.ProductoCreadoResponse;
import com.tiendatech.productos.presentation.dto.CrearProductoRequest;
import com.tiendatech.productos.application.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
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
        CrearProductoRequest request = new CrearProductoRequest();
        request.setCategoriaId(2);
        request.setNombre("Producto de prueba");

        ResponseEntity<ProductoCreadoResponse> response = controller.crearProducto(
                request,
                "jose");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/api/productos/42"));
        assertThat(response.getBody()).isEqualTo(new ProductoCreadoResponse(true, 42L));
    }
}
