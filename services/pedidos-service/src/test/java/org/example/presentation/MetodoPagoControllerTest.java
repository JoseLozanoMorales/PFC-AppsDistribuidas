package org.example.presentation;

import org.example.application.MetodoPagoService;
import org.example.domain.MetodoPago;
import org.example.infrastructure.config.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Verificacion de propiedad de MetodoPagoController: metodo de pago ajeno o
 * inexistente -> 404 uniforme (el repositorio, mockeado aqui via el servicio,
 * ya filtra por metodopagoId+usuarioId en la misma consulta -- ver
 * MetodoPagoServiceTest -- asi que ambos casos llegan como null al
 * controlador y producen exactamente el mismo 404).
 */
@ExtendWith(MockitoExtension.class)
class MetodoPagoControllerTest {

    @Mock
    private MetodoPagoService metodoPagoService;

    private MetodoPagoController controller;

    private static final AuthenticatedUser CLIENTE = new AuthenticatedUser(8, "cliente8", "CLIENTE");

    @Test
    void obtenerPorId_ajenoOInexistente_lanza404() {
        controller = new MetodoPagoController(metodoPagoService);
        when(metodoPagoService.obtenerPorIdYUsuario(999, 8)).thenReturn(null);

        assertThatThrownBy(() -> controller.obtenerPorId(999, CLIENTE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerPorId_propio_loDevuelve() {
        controller = new MetodoPagoController(metodoPagoService);
        MetodoPago propio = new MetodoPago(10, "**** 1234", LocalDate.of(2027, 1, 1), true, 1, "Visa");
        when(metodoPagoService.obtenerPorIdYUsuario(10, 8)).thenReturn(propio);

        MetodoPago resultado = controller.obtenerPorId(10, CLIENTE);

        assertThat(resultado).isSameAs(propio);
    }

    @Test
    void listarPorUsuario_usuarioAjenoEnLaRuta_lanza404() {
        controller = new MetodoPagoController(metodoPagoService);

        assertThatThrownBy(() -> controller.listarPorUsuario(99, null, null, CLIENTE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
