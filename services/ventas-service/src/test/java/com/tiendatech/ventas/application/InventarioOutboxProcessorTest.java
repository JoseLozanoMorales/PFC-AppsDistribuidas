package com.tiendatech.ventas.application;

import com.tiendatech.ventas.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class InventarioOutboxProcessorTest {
    @Test void procesaPendientesYRegistraFallosSinDetenerElLote() {
        var outbox = mock(FacturaOutboxStore.class); var facturas = mock(FacturaStore.class); var inventario = mock(InventarioPort.class);
        var detalle = List.of(new FacturaDetalle());
        when(outbox.facturasPendientes(5,20)).thenReturn(List.of(1,2));
        when(facturas.listarDetalle(1)).thenReturn(detalle); when(facturas.listarDetalle(2)).thenReturn(detalle);
        doThrow(new RuntimeException("inventario no disponible")).when(inventario).registrarSalidasPorFactura(2,detalle,"outbox-retry");
        new InventarioOutboxProcessor(outbox,facturas,inventario).reintentarPendientes();
        verify(outbox).marcarProcesado(1); verify(outbox).registrarFallo(2,"inventario no disponible",5);
    }
}
