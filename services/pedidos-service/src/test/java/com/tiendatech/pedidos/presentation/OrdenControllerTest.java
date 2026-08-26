package com.tiendatech.pedidos.presentation;

import com.tiendatech.pedidos.application.OrdenService;
import com.tiendatech.pedidos.domain.Orden;
import com.tiendatech.pedidos.domain.PageResponse;
import com.tiendatech.pedidos.domain.Paginacion;
import com.tiendatech.pedidos.infrastructure.config.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la verificacion de propiedad y del rol ADMIN en OrdenController --
 * la correccion de seguridad mas importante de este servicio. NOTA: esto vive
 * en com.tiendatech.pedidos.presentation, no en com.tiendatech.pedidos.application (OrdenService ni
 * siquiera recibe el usuarioId autenticado); se testea aqui porque es donde
 * realmente ocurre. El controlador no tiene anotaciones de Spring en el
 * constructor mas alla de @Autowired, asi que se instancia directo con un
 * OrdenService mockeado, sin contexto de Spring ni MockMvc.
 */
@ExtendWith(MockitoExtension.class)
class OrdenControllerTest {

    @Mock
    private OrdenService ordenService;

    private OrdenController controller;

    private static final AuthenticatedUser CLIENTE = new AuthenticatedUser(8, "cliente8", "CLIENTE");
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1, "admin1", "ADMIN");

    private static Orden ordenDe(Integer ordenId, Integer usuarioId) {
        return new Orden(ordenId, usuarioId, 3, 2, new BigDecimal("100.00"), new BigDecimal("115.00"),
                LocalDate.of(2026, 8, 18));
    }

    // ------------------------------------------------------------------
    // GET /api/ordenes -- solo ADMIN
    // ------------------------------------------------------------------

    @Test
    void listarOrdenes_conRolAdmin_devuelveTodasLasOrdenes() {
        controller = new OrdenController(ordenService);
        PageResponse<Orden> pagina = new PageResponse<>(List.of(ordenDe(1, 8), ordenDe(2, 99)), 0, 20, 2, 1);
        when(ordenService.listarOrdenes(Paginacion.de(null, null))).thenReturn(pagina);

        PageResponse<com.tiendatech.pedidos.presentation.dto.OrdenResponse> resultado =
                controller.listarOrdenes(null, null, ADMIN);

        assertThat(resultado.content()).hasSize(2);
        verify(ordenService).listarOrdenes(Paginacion.de(null, null));
    }

    @Test
    void listarOrdenes_sinRolAdmin_lanza403() {
        controller = new OrdenController(ordenService);

        assertThatThrownBy(() -> controller.listarOrdenes(null, null, CLIENTE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // GET /api/ordenes/{id} -- orden ajena = 404, indistinguible de "no existe"
    // ------------------------------------------------------------------

    @Test
    void obtenerOrden_ajena_lanza404() {
        controller = new OrdenController(ordenService);
        when(ordenService.obtenerOrdenPorId(1)).thenReturn(ordenDe(1, 99)); // es de OTRO_CLIENTE

        assertThatThrownBy(() -> controller.obtenerOrden(1, CLIENTE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerOrden_inexistente_lanza404ConElMismoMensajeQueLaAjena() {
        controller = new OrdenController(ordenService);
        when(ordenService.obtenerOrdenPorId(404)).thenReturn(null);

        ResponseStatusException excepcionInexistente = catchResponseStatusException(
                () -> controller.obtenerOrden(404, CLIENTE));

        when(ordenService.obtenerOrdenPorId(1)).thenReturn(ordenDe(1, 99));
        ResponseStatusException excepcionAjena = catchResponseStatusException(
                () -> controller.obtenerOrden(1, CLIENTE));

        // Mismo status Y mismo formato de mensaje ("Orden {id} no encontrada"):
        // un atacante no puede distinguir "no existe" de "es de otro usuario".
        assertThat(excepcionInexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(excepcionAjena.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(excepcionInexistente.getReason()).isEqualTo("Orden 404 no encontrada");
        assertThat(excepcionAjena.getReason()).isEqualTo("Orden 1 no encontrada");
    }

    @Test
    void obtenerOrden_propia_devuelveLaOrden() {
        controller = new OrdenController(ordenService);
        when(ordenService.obtenerOrdenPorId(1)).thenReturn(ordenDe(1, 8));

        var respuesta = controller.obtenerOrden(1, CLIENTE);

        assertThat(respuesta.ordenId()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // GET /api/ordenes/usuario/{usuarioId} -- usuarioId de otro = 404
    // ------------------------------------------------------------------

    @Test
    void obtenerOrdenesPorUsuario_ajeno_lanza404() {
        controller = new OrdenController(ordenService);

        assertThatThrownBy(() -> controller.obtenerOrdenesPorUsuario(99, null, null, CLIENTE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerOrdenesPorUsuario_propio_listaSusOrdenes() {
        controller = new OrdenController(ordenService);
        PageResponse<Orden> pagina = new PageResponse<>(List.of(ordenDe(1, 8)), 0, 20, 1, 1);
        when(ordenService.listarOrdenesPorUsuario(8, Paginacion.de(null, null))).thenReturn(pagina);

        var resultado = controller.obtenerOrdenesPorUsuario(8, null, null, CLIENTE);

        assertThat(resultado.content()).hasSize(1);
    }

    // ------------------------------------------------------------------
    // GET /api/ordenes/{id}/detalle -- misma regla de propiedad
    // ------------------------------------------------------------------

    @Test
    void obtenerDetalleOrden_ajena_lanza404_yNuncaConsultaElDetalle() {
        controller = new OrdenController(ordenService);
        when(ordenService.obtenerOrdenPorId(1)).thenReturn(ordenDe(1, 99));

        assertThatThrownBy(() -> controller.obtenerDetalleOrden(1, null, null, CLIENTE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException catchResponseStatusException(Runnable accion) {
        try {
            accion.run();
        } catch (ResponseStatusException e) {
            return e;
        }
        throw new AssertionError("Se esperaba ResponseStatusException y no se lanzo ninguna");
    }
}
