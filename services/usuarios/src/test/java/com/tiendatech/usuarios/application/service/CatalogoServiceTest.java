package com.tiendatech.usuarios.application.service;

import com.tiendatech.usuarios.application.dto.*;
import com.tiendatech.usuarios.domain.model.*;
import com.tiendatech.usuarios.domain.port.out.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogoServiceTest {
    @Test void gestionaProvincias() {
        var repo=mock(ProvinciaRepositoryPort.class); var service=new ProvinciaService(repo); when(repo.findAllByName()).thenReturn(List.of(new Provincia((short)1,"Guayas")));
        assertEquals("Guayas",service.listar().getFirst().getNombre()); var dto=new ProvinciaDTO(); dto.setNombre(" Manabi "); service.crear(dto); service.actualizar(1L,dto); service.eliminar(1L);
        verify(repo).create("Manabi");verify(repo).update(1L,"Manabi");verify(repo).disable(1L);dto.setNombre(" ");assertThrows(IllegalArgumentException.class,()->service.crear(dto));
    }
    @Test void gestionaCiudades() {
        var repo=mock(CiudadRepositoryPort.class);var service=new CiudadService(repo);when(repo.findAllByName()).thenReturn(List.of(new Ciudad((short)2,"Guayaquil",(short)1)));
        assertEquals("Guayaquil",service.listar().getFirst().getNombre());var dto=new CiudadDTO();dto.setNombre(" Duran ");dto.setProvinciaId((short)1);service.crear(dto);service.actualizar((short)2,dto);service.eliminar((short)2);
        verify(repo).create("Duran",(short)1);verify(repo).update((short)2,"Duran",(short)1);verify(repo).disable((short)2);dto.setProvinciaId(null);assertThrows(IllegalArgumentException.class,()->service.crear(dto));
    }
    @Test void gestionaDireccionesDelUsuario() {
        var repo=mock(DireccionRepositoryPort.class);var service=new DireccionService(repo);var direccion=new Direccion((short)3,7,(short)2,"Guayaquil","Guayas","Av. 9","Casa",true);
        when(repo.findEnabledByUser(7)).thenReturn(List.of(direccion));when(repo.findDetailedByUser(7)).thenReturn(List.of(direccion));when(repo.create(7,(short)2,"Av. 9","Casa")).thenReturn(direccion);when(repo.update(7,(short)3,(short)2,"Av. 9","Casa")).thenReturn(direccion);
        assertEquals("Av. 9",service.listar(7).getFirst().getCalle());var dto=new DireccionDTO();dto.setCiudadId((short)2);dto.setCalle(" Av. 9 ");dto.setReferencia(" Casa ");assertEquals((short)3,service.crear(7,dto).getDireccionId());assertEquals((short)3,service.actualizar(7,(short)3,dto).getDireccionId());assertEquals(1,service.listarDetallado(7).size());service.eliminar(7,(short)3);verify(repo).disable(7,(short)3);
        dto.setCalle(" ");assertThrows(IllegalArgumentException.class,()->service.crear(7,dto));
    }
}
