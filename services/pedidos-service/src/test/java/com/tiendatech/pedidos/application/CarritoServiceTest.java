package com.tiendatech.pedidos.application;

import com.tiendatech.pedidos.domain.Carrito;
import com.tiendatech.pedidos.domain.CarritoDetalle;
import com.tiendatech.pedidos.domain.CarritoRepository;
import com.tiendatech.pedidos.domain.PageResponse;
import com.tiendatech.pedidos.domain.ProductoInfo;
import com.tiendatech.pedidos.domain.ProductoPort;
import com.tiendatech.pedidos.domain.ReservationPort;
import com.tiendatech.pedidos.domain.ReservationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de CarritoService. La unica logica de negocio real (no delegacion
 * pura) es agregarProducto: el precio SIEMPRE se consulta a productos-service,
 * nunca se confia en lo que mande el cliente (ver comentario en el codigo
 * fuente) -- esa es la regla que estos tests protegen.
 */
@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    @Mock
    private CarritoRepository carritoRepository;
    @Mock
    private ProductoPort productoClient;
    @Mock
    private ReservationPort reservationClient;
    @Mock
    private CartLamportClock lamportClock;

    private CarritoService service;

    @Test
    void agregarProducto_usaElPrecioDeProductosServicio_noElQueMandaElCliente() {
        service = new CarritoService(carritoRepository, productoClient);
        ProductoInfo infoReal = new ProductoInfo(9, new BigDecimal("199.99"), 1, new BigDecimal("15"));
        when(productoClient.obtenerPrecioEIva(9)).thenReturn(infoReal);

        service.agregarProducto(carritoId(), 9, 2);

        verify(productoClient).obtenerPrecioEIva(9);
        verify(carritoRepository).agregarProducto(carritoId(), 9, 2, new BigDecimal("199.99"));
    }

    @Test
    void quitarProducto_yActualizarCantidad_delegan_al_repositorio_con_los_mismos_parametros() {
        service = new CarritoService(carritoRepository, productoClient);

        service.quitarProducto(carritoId(), 9);
        service.actualizarCantidad(carritoId(), 9, 5);

        verify(carritoRepository).quitarProducto(carritoId(), 9);
        verify(carritoRepository).actualizarCantidad(carritoId(), 9, 5);
    }

    @Test
    void metodosSimplesDeConsultaDelegan_alRepositorio() {
        service = new CarritoService(carritoRepository, productoClient);
        var carrito = new Carrito(carritoId(), 7, new BigDecimal("10.00"), true);
        when(carritoRepository.obtenerActivo(7)).thenReturn(carrito);
        when(carritoRepository.obtenerPorId(carritoId())).thenReturn(carrito);
        when(carritoRepository.crear(7)).thenReturn(carrito);
        var pagina = new PageResponse<CarritoDetalle>(List.of(), 0, 20, 0, 0);
        var paginacion = com.tiendatech.pedidos.domain.Paginacion.de(0, 20);
        when(carritoRepository.listarDetalle(carritoId(), paginacion)).thenReturn(pagina);

        assertSame(carrito, service.obtenerCarritoActivo(7));
        assertSame(carrito, service.obtenerCarritoPorId(carritoId()));
        assertSame(carrito, service.crearCarrito(7));
        assertSame(pagina, service.listarDetalle(carritoId(), paginacion));
    }

    @Test
    void agregarProductoConReserva_fallaSiNoSeConfiguroElCanalDeReservas() {
        service = new CarritoService(carritoRepository, productoClient);
        var vacio = new PageResponse<CarritoDetalle>(List.of(), 0, 100, 0, 0);
        when(carritoRepository.listarDetalle(eq(carritoId()), any())).thenReturn(vacio);

        assertThrows(IllegalStateException.class, () ->
                service.agregarProducto(carritoId(), 5, 9, 2, "dev-1", 1L, "op-1"));
    }

    @Test
    void agregarProductoConReserva_reconciliaEInsertaCuandoNoHabiaCantidadPrevia() {
        service = new CarritoService(carritoRepository, productoClient);
        service.configureReservations(reservationClient, lamportClock);

        var vacio = new PageResponse<CarritoDetalle>(List.of(), 0, 100, 0, 0);
        when(carritoRepository.listarDetalle(eq(carritoId()), any())).thenReturn(vacio);
        var infoReal = new ProductoInfo(9, new BigDecimal("50.00"), 1, new BigDecimal("15"));
        when(productoClient.obtenerPrecioEIva(9)).thenReturn(infoReal);
        when(lamportClock.receive(42L, 1L)).thenReturn(7L);
        var reservado = new ReservationResult(true, "ok", 2, 8, 9L, "dev-1", false);
        when(reservationClient.reconcile(any())).thenReturn(reservado);

        var result = service.agregarProducto(carritoId(), 5, 9, 2, "dev-1", 1L, "op-1");

        assertSame(reservado, result);
        verify(lamportClock).receiveResponse(42L, 9L);
        verify(carritoRepository).agregarProducto(carritoId(), 9, 2, new BigDecimal("50.00"));
    }

    @Test
    void actualizarCantidadConReserva_quitaElProductoCuandoLaCantidadQuedaEnCero() {
        service = new CarritoService(carritoRepository, productoClient);
        service.configureReservations(reservationClient, lamportClock);

        var vacio = new PageResponse<CarritoDetalle>(List.of(), 0, 100, 0, 0);
        when(carritoRepository.listarDetalle(eq(carritoId()), any())).thenReturn(vacio);
        when(lamportClock.receive(42L, 3L)).thenReturn(4L);
        var resultado = new ReservationResult(true, "ok", 0, 10, 5L, "dev-2", false);
        when(reservationClient.reconcile(any())).thenReturn(resultado);

        var result = service.actualizarCantidad(carritoId(), 5, 9, 0, "dev-2", 3L, "op-2");

        assertSame(resultado, result);
        verify(productoClient, never()).obtenerPrecioEIva(9);
        verify(carritoRepository).quitarProducto(carritoId(), 9);
    }

    @Test
    void actualizarCantidadConReserva_actualizaCuandoYaHabiaCantidadPrevia() {
        service = new CarritoService(carritoRepository, productoClient);
        service.configureReservations(reservationClient, lamportClock);

        var detalle = new CarritoDetalle(carritoId(), 9, 3, new BigDecimal("20.00"));
        var conContenido = new PageResponse<CarritoDetalle>(List.of(detalle), 0, 100, 1, 1);
        when(carritoRepository.listarDetalle(eq(carritoId()), any())).thenReturn(conContenido);
        when(lamportClock.receive(42L, 2L)).thenReturn(6L);
        var resultado = new ReservationResult(true, "ok", 5, 10, 6L, "dev-3", false);
        when(reservationClient.reconcile(any())).thenReturn(resultado);
        var infoReal = new ProductoInfo(9, new BigDecimal("20.00"), 1, new BigDecimal("15"));
        when(productoClient.obtenerPrecioEIva(9)).thenReturn(infoReal);

        var result = service.actualizarCantidad(carritoId(), 5, 9, 5, "dev-3", 2L, "op-3");

        assertSame(resultado, result);
        verify(carritoRepository).actualizarCantidad(carritoId(), 9, 5);
    }

    @Test
    void snapshotForCheckoutDevuelveNullSiNoHayCarritoActivo() {
        service = new CarritoService(carritoRepository, productoClient);
        when(carritoRepository.obtenerActivo(7)).thenReturn(null);

        assertNull(service.snapshotForCheckout(7));
    }

    @Test
    void snapshotForCheckoutArmaElListadoDeProductosDelCarritoActivo() {
        service = new CarritoService(carritoRepository, productoClient);
        var carrito = new Carrito(carritoId(), 7, new BigDecimal("0"), true);
        when(carritoRepository.obtenerActivo(7)).thenReturn(carrito);
        var detalle = new CarritoDetalle(carritoId(), 9, 2, new BigDecimal("20.00"));
        var pagina = new PageResponse<CarritoDetalle>(List.of(detalle), 0, 100, 1, 1);
        when(carritoRepository.listarDetalle(eq(carritoId()), any())).thenReturn(pagina);

        var snapshot = service.snapshotForCheckout(7);

        assertEquals(carritoId(), snapshot.cartId());
        assertEquals(7, snapshot.userId());
        assertEquals(List.of(9), snapshot.productIds());
    }

    @Test
    void releaseAfterCheckoutNoHaceNadaSiElSnapshotEsNull() {
        service = new CarritoService(carritoRepository, productoClient);

        assertDoesNotThrow(() -> service.releaseAfterCheckout(null));
    }

    @Test
    void releaseAfterCheckoutLiberaCadaProductoYToleraFallosDeReserva() {
        service = new CarritoService(carritoRepository, productoClient);
        service.configureReservations(reservationClient, lamportClock);
        when(lamportClock.receive(eq(42L), eq(0L))).thenReturn(1L);
        when(reservationClient.reconcile(any()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(new ReservationResult(true, "ok", 0, 10, 2L, "checkout", false));

        var snapshot = new CarritoService.CartReservationSnapshot(carritoId(), 7, List.of(9, 11));

        assertDoesNotThrow(() -> service.releaseAfterCheckout(snapshot));
        verify(reservationClient, times(2)).reconcile(any());
    }

    private static Integer carritoId() {
        return 42;
    }
}
