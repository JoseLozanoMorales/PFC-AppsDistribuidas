package org.example.application;

import org.example.domain.CarritoRepository;
import org.example.domain.ProductoInfo;
import org.example.domain.ProductoPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private static Integer carritoId() {
        return 42;
    }
}
