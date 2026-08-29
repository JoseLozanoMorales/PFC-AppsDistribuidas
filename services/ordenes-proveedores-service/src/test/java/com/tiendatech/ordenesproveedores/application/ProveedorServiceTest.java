package com.tiendatech.ordenesproveedores.application;

import com.tiendatech.ordenesproveedores.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProveedorServiceTest {
    private final ProveedorRepository repository = mock(ProveedorRepository.class);
    private final ProveedorService service = new ProveedorService(repository);

    @Test void ejecutaCicloDeVidaDelProveedor() {
        var proveedor = new Proveedor(); proveedor.setNombre("Tech Supply");
        when(repository.crear(proveedor)).thenReturn(4); when(repository.obtenerPorId(4)).thenReturn(proveedor);
        when(repository.listarTodos()).thenReturn(List.of(proveedor));
        assertEquals(4, service.crear(proveedor)); service.actualizar(4, proveedor);
        assertEquals(4, proveedor.getProveedorId()); service.desactivar(4); service.activar(4);
        assertSame(proveedor, service.obtenerPorId(4)); assertEquals(List.of(proveedor), service.listarTodos());
        verify(repository).actualizar(proveedor); verify(repository).desactivar(4); verify(repository).activar(4);
    }

    @Test void devuelve404CuandoProveedorNoExiste() {
        var error = assertThrows(ResponseStatusException.class, () -> service.obtenerPorId(99));
        assertEquals(404, error.getStatusCode().value());
    }
}
