package com.tiendatech.ordenesproveedores.application;

import com.tiendatech.ordenesproveedores.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrdenCompraServiceTest {
    private final OrdenCompraRepository repository = mock(OrdenCompraRepository.class);
    private final InventarioPort inventario = mock(InventarioPort.class);
    private final OrdenCompraService service = new OrdenCompraService(repository, inventario);

    @Test void delegaCicloDeVidaYConsultas() {
        var detalle = List.of(new DetalleOrdenCompra()); var fecha = LocalDate.of(2026,9,1); var orden = new OrdenCompra();
        when(repository.crear(2,3,fecha,detalle)).thenReturn(8); when(repository.obtenerPorId(8)).thenReturn(orden);
        when(repository.listarPorEstado(EstadoOrdenCompra.PENDIENTE)).thenReturn(List.of(orden)); when(repository.listarDetalle(8)).thenReturn(detalle);
        assertEquals(8, service.crear(2,3,fecha,detalle)); service.actualizar(8,2,fecha,detalle); service.enviar(8); service.cancelar(8);
        assertEquals(List.of(orden), service.listarPorEstado(EstadoOrdenCompra.PENDIENTE)); assertSame(orden, service.obtenerPorId(8));
        assertEquals(detalle, service.listarDetalle(8)); verify(repository).actualizar(8,2,fecha,detalle); verify(repository).enviar(8); verify(repository).cancelar(8);
    }

    @Test void registraRecepcionEnInventarioConCostosNegociados() {
        var cantidades = Map.of(10,3); var costos = Map.of(10,new BigDecimal("15.50"));
        when(repository.registrarRecepcion(8,cantidades)).thenReturn(costos);
        service.registrarRecepcion(8,cantidades,"ana");
        verify(inventario).registrarEntradasPorRecepcion(8,cantidades,costos,"ana");
    }

    @Test void conservaMensajeJsonCuandoInventarioFalla() {
        var cantidades=Map.of(10,3); var costos=Map.of(10,BigDecimal.TEN);
        when(repository.registrarRecepcion(8,cantidades)).thenReturn(costos);
        var remote = HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,"error",null,"{\"error\":\"stock bloqueado\"}".getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);
        doThrow(remote).when(inventario).registrarEntradasPorRecepcion(8,cantidades,costos,"ana");
        var error=assertThrows(IllegalStateException.class,()->service.registrarRecepcion(8,cantidades,"ana"));
        assertTrue(error.getMessage().contains("stock bloqueado"));
    }

    @Test void validaExistenciaAntesDeDetalle() {
        var error=assertThrows(ResponseStatusException.class,()->service.listarDetalle(99));
        assertEquals(404,error.getStatusCode().value()); verify(repository,never()).listarDetalle(99);
    }
}
