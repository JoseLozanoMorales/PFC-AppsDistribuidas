package com.tiendatech.productos.application;

import com.tiendatech.productos.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductoServiceTest {
    private final ProductoRepository repository = mock(ProductoRepository.class);
    private final ProductoService service = new ProductoService(repository);

    @Test void delegaLecturasDelCatalogo() {
        when(repository.listar(0, 20)).thenReturn(List.of());
        when(repository.masVendidos(5)).thenReturn(List.of(Map.of("id", 1)));
        when(repository.recientesMenu(4)).thenReturn(List.of(Map.of("id", 2)));
        when(repository.categorias()).thenReturn(List.of(Map.of("nombre", "CPU")));
        when(repository.marcas()).thenReturn(List.of()); when(repository.gamas()).thenReturn(List.of());
        when(repository.buscar(Map.of("q", "ryzen"))).thenReturn(List.of(Map.of("id", 3)));
        when(repository.porCategoria(2, 0, 10)).thenReturn(List.of());
        when(repository.detalle(3)).thenReturn(Map.of("id", 3));
        when(repository.detalleParaEditar(3)).thenReturn(List.of(Map.of("id", 3)));
        when(repository.listarIvas()).thenReturn(List.of());
        assertEquals(0, service.listar(0, 20).size()); assertEquals(1, service.masVendidos(5).size());
        assertEquals(1, service.recientesMenu(4).size()); assertEquals(1, service.categorias().size());
        assertTrue(service.marcas().isEmpty()); assertTrue(service.gamas().isEmpty());
        assertEquals(1, service.buscar(Map.of("q", "ryzen")).size()); assertTrue(service.porCategoria(2,0,10).isEmpty());
        assertEquals(3, service.detalle(3).get("id")); assertEquals(1, service.detalleParaEditar(3).size());
        assertTrue(service.listarIvas().isEmpty());
    }

    @Test void delegaGaleriaYEscrituras() {
        var media = new MediaProducto(new byte[]{1}, "image/png", 1L);
        var image = new ImagenProducto(new byte[]{1}, "image/png", 1);
        when(repository.galeriaContenido(7)).thenReturn(media);
        when(repository.galeriaProducto(3, "public")).thenReturn(List.of(Map.of("id", 7)));
        when(repository.crear(eq(2), anyMap(), eq("admin"))).thenReturn(3L);
        when(repository.agregarImagen(3, image, "portada", true)).thenReturn(7L);
        assertSame(media, service.galeriaContenido(7)); assertEquals(1, service.galeriaProducto(3,"public").size());
        assertEquals(3L, service.crear(2, Map.of("nombre","CPU"), "admin"));
        assertEquals(7L, service.agregarImagen(3, image, "portada", true));
        service.quitarImagen(7); service.ordenarGaleria(3, List.of(7)); service.eliminar(3,"admin");
        service.actualizarBasico(3, Map.of("nombre","CPU 2"), "admin"); service.activar(3);
        verify(repository).quitarImagen(7); verify(repository).ordenarGaleria(3,List.of(7));
        verify(repository).eliminar(3,"admin"); verify(repository).actualizarBasico(3,Map.of("nombre","CPU 2"),"admin"); verify(repository).activar(3);
    }
}
