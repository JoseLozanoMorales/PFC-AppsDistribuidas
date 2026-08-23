package org.example.application;

import org.example.domain.MetodoPago;
import org.example.domain.MetodoPagoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetodoPagoService es delegacion pura hacia MetodoPagoRepository: no hay
 * calculo propio. Lo que SI vale la pena proteger con un test es que
 * usuarioId llegue intacto al repositorio en cada operacion -- es el
 * parametro que hace que la consulta SQL (WHERE ... AND usuario_id = ?) filtre
 * por dueno, la base de la verificacion de propiedad de este recurso.
 */
@ExtendWith(MockitoExtension.class)
class MetodoPagoServiceTest {

    @Mock
    private MetodoPagoRepository metodoPagoRepository;

    private MetodoPagoService service;

    @Test
    void obtenerPorIdYUsuario_delega_con_ambos_ids_y_devuelve_lo_que_da_el_repositorio() {
        service = new MetodoPagoService(metodoPagoRepository);
        MetodoPago esperado = new MetodoPago(10, "**** 1234", LocalDate.of(2027, 1, 1), true, 1, "Visa");
        when(metodoPagoRepository.obtenerPorIdYUsuario(10, 8)).thenReturn(esperado);

        MetodoPago resultado = service.obtenerPorIdYUsuario(10, 8);

        assertThat(resultado).isSameAs(esperado);
        verify(metodoPagoRepository).obtenerPorIdYUsuario(10, 8);
    }

    @Test
    void obtenerPorIdYUsuario_metodoAjenoOInexistente_devuelveNullSinDistinguir() {
        // El repositorio filtra por (metodopago_id, usuario_id) en la MISMA
        // consulta: "no existe" y "existe pero es de otro usuario" devuelven el
        // mismo null desde aqui -- es la base de que el 404 del controlador
        // (ver presentation.MetodoPagoControllerTest) sea uniforme.
        service = new MetodoPagoService(metodoPagoRepository);
        when(metodoPagoRepository.obtenerPorIdYUsuario(999, 8)).thenReturn(null);

        MetodoPago resultado = service.obtenerPorIdYUsuario(999, 8);

        assertThat(resultado).isNull();
    }

    @Test
    void actualizar_propagaUsuarioIdParaQueElRepositorioNoActualiceUnMetodoAjeno() {
        service = new MetodoPagoService(metodoPagoRepository);

        service.actualizar(10, 8, "**** 1234", LocalDate.of(2027, 1, 1), 1, true);

        verify(metodoPagoRepository).actualizar(10, 8, "**** 1234", LocalDate.of(2027, 1, 1), 1, true);
    }

    @Test
    void inactivar_yReactivar_propagan_metodopagoId_y_usuarioId() {
        service = new MetodoPagoService(metodoPagoRepository);

        service.inactivar(10, 8);
        service.reactivar(10, 8);

        verify(metodoPagoRepository).inactivar(10, 8);
        verify(metodoPagoRepository).reactivar(10, 8);
    }

    @Test
    void agregar_propagaUsuarioIdYDevuelveElIdGeneradoPorElRepositorio() {
        service = new MetodoPagoService(metodoPagoRepository);
        when(metodoPagoRepository.agregar("**** 1234", LocalDate.of(2027, 1, 1), 1, 8)).thenReturn(77);

        Integer id = service.agregar("**** 1234", LocalDate.of(2027, 1, 1), 1, 8);

        assertThat(id).isEqualTo(77);
    }
}
