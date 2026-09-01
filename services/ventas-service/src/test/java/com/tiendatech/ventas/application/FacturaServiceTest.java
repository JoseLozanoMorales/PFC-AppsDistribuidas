package com.tiendatech.ventas.application;

import com.tiendatech.ventas.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FacturaServiceTest {
    private final FacturaStore store = mock(FacturaStore.class);
    private final FacturaService service = new FacturaService(store);

    @Test void generaObtieneYListaFacturas() {
        var factura = new Factura(); factura.setFacturaId(9);
        var draft = new FacturaDraft(4, LocalDate.of(2026, 8, 31), 8,
                new BigDecimal("10.00"), new BigDecimal("11.50"), List.of());
        when(store.generar(draft)).thenReturn(9); when(store.obtenerPorId(9)).thenReturn(factura);
        when(store.listar(2)).thenReturn(List.of(factura));
        assertEquals(9, service.generar(draft)); assertSame(factura, service.obtenerPorId(9));
        assertEquals(List.of(factura), service.listar(2));
    }

    @Test void devuelve404SiFacturaNoExiste() {
        var error = assertThrows(ResponseStatusException.class, () -> service.obtenerPorId(99));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test void validaFacturaAntesDeListarDetalle() {
        var factura = new Factura(); var detalle = new FacturaDetalle();
        when(store.obtenerPorId(9)).thenReturn(factura); when(store.listarDetalle(9)).thenReturn(List.of(detalle));
        assertEquals(List.of(detalle), service.listarDetalle(9));
        verify(store).obtenerPorId(9); verify(store).listarDetalle(9);
    }
}
