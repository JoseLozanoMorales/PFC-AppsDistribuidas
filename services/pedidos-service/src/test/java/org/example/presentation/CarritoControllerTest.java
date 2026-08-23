package org.example.presentation;

import org.example.application.CarritoService;
import org.example.domain.Carrito;
import org.example.infrastructure.config.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verificacion de propiedad de CarritoController: carrito ajeno o inexistente
 * -> 404 uniforme. Igual que en OrdenControllerTest, esto vive en
 * org.example.presentation, no en el servicio de aplicacion.
 */
@ExtendWith(MockitoExtension.class)
class CarritoControllerTest {

    @Mock
    private CarritoService carritoService;

    private CarritoController controller;

    private static final AuthenticatedUser CLIENTE = new AuthenticatedUser(8, "cliente8", "CLIENTE");

    @Test
    void obtenerCarrito_usuarioAjenoEnLaRuta_lanza404() {
        controller = new CarritoController(carritoService);

        assertThatThrownBy(() -> controller.obtenerCarrito(99, CLIENTE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerCarrito_propio_loDevuelve() {
        controller = new CarritoController(carritoService);
        Carrito propio = new Carrito(5, 8, BigDecimal.ZERO, true);
        when(carritoService.obtenerCarritoActivo(8)).thenReturn(propio);

        Carrito resultado = controller.obtenerCarrito(8, CLIENTE);

        assertThat(resultado).isSameAs(propio);
    }

    @Test
    void obtenerDetalle_carritoDeOtroUsuario_lanza404() {
        controller = new CarritoController(carritoService);
        Carrito ajeno = new Carrito(5, 99, BigDecimal.ZERO, true);
        when(carritoService.obtenerCarritoPorId(5)).thenReturn(ajeno);

        assertThatThrownBy(() -> controller.obtenerDetalle(5, null, null, CLIENTE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerDetalle_carritoInexistente_lanza404ConElMismoStatusQueElAjeno() {
        // carritoService.obtenerCarritoPorId(...) == null representa "no existe".
        // Mismo status y mismo mensaje que el caso "existe pero es de otro
        // usuario" -- ver verificarPropietarioDeCarrito en el controlador.
        controller = new CarritoController(carritoService);
        when(carritoService.obtenerCarritoPorId(404)).thenReturn(null);

        assertThatThrownBy(() -> controller.obtenerDetalle(404, null, null, CLIENTE))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("reason", "Carrito no encontrado")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void agregarProducto_carritoAjeno_lanza404_yNuncaLlegaAlServicio() {
        controller = new CarritoController(carritoService);
        Carrito ajeno = new Carrito(5, 99, BigDecimal.ZERO, true);
        when(carritoService.obtenerCarritoPorId(5)).thenReturn(ajeno);

        assertThatThrownBy(() -> controller.agregarProducto(5, Map.of("productoId", 1, "cantidad", 2), CLIENTE))
                .isInstanceOf(ResponseStatusException.class);

        verify(carritoService, never()).agregarProducto(any(), any(), any());
    }
}
