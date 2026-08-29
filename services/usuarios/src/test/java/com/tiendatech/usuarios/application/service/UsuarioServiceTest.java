package com.tiendatech.usuarios.application.service;

import com.tiendatech.usuarios.application.dto.*;
import com.tiendatech.usuarios.domain.model.*;
import com.tiendatech.usuarios.domain.port.out.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UsuarioServiceTest {
    private final UsuarioRepositoryPort repository=mock(UsuarioRepositoryPort.class);
    private final PasswordHasher hasher=mock(PasswordHasher.class);
    private final OtpService otp=mock(OtpService.class);
    private final UsuarioQueryPort query=mock(UsuarioQueryPort.class);
    private final AdminUserPort admin=mock(AdminUserPort.class);
    private final UsuarioService service=new UsuarioService(repository,hasher,otp,query,admin);
    private final Usuario user=new Usuario(7,"Ana","091","ana@x.com","099","ana","hash",true,(short)1,(short)2,null);

    @Test void obtieneYValidaLogin() {
        when(repository.findById(7)).thenReturn(Optional.of(user)); when(repository.findByUsername("ana")).thenReturn(Optional.of(user));
        when(hasher.matches("secreta","hash")).thenReturn(true);
        assertSame(user,service.getById(7)); assertSame(user,service.login("ana","secreta"));
        assertThrows(IllegalArgumentException.class,()->service.login("nadie","x"));
        when(hasher.matches("mala","hash")).thenReturn(false); assertThrows(IllegalArgumentException.class,()->service.login("ana","mala"));
    }

    @Test void registraClienteConPasswordHasheado() {
        var dto=dto((short)2,"clave-segura"); when(hasher.hash("clave-segura")).thenReturn("hash-nuevo"); when(repository.findByUsername("ana")).thenReturn(Optional.of(user));
        assertSame(user,service.crearClienteConSP(dto));
        verify(repository).registerClient("Ana","091","ana@x.com","099","ana","hash-nuevo");
        dto.setContrasena(" "); assertThrows(IllegalArgumentException.class,()->service.crearClienteConSP(dto));
    }

    @Test void actualizaClienteNormalizandoYHasheando() {
        var req=new ClienteUpdateRequest(); req.setNombre(" Ana "); req.setCorreo(" "); req.setContrasena("nueva-clave");
        when(hasher.hash("nueva-clave")).thenReturn("nuevo-hash"); service.actualizarCliente(7,req);
        verify(repository).updateClient(7,"Ana",null,null,null,null,"nuevo-hash");
    }

    @Test void creaAdministradorConCredencialGenerada() {
        var dto=dto((short)1,null); when(otp.generarYEnviarCredenciales("ana@x.com","ana",12)).thenReturn("Temporal9");
        when(hasher.hash("Temporal9")).thenReturn("hash-temp"); when(repository.findByUsername("ana")).thenReturn(Optional.of(user));
        assertSame(user,service.crearAdminOTrabajador(dto)); verify(admin).execute(argThat(items->items.size()==1 && "AGREGAR".equals(items.getFirst().action())));
        dto.setIdRol((short)2); assertThrows(IllegalArgumentException.class,()->service.crearAdminOTrabajador(dto));
    }

    @Test void actualizaDeshabilitaYBuscaAdministradores() {
        var req=new ClienteUpdateRequest(); req.setNombre(" Ana "); req.setContrasena("nueva"); when(hasher.hash("nueva")).thenReturn("hash2");
        service.actualizarAdmin(7,1,req); service.deshabilitarAdmin(7,1);
        verify(admin,times(2)).execute(anyList());
        when(repository.searchByUsername("ana",2)).thenReturn(List.of(user,user,user));
        assertEquals(2,service.buscarPorUsuario(" ana ",2,2).size()); assertEquals(3,service.buscarPorUsuario("ana",2,99).size());
    }

    @Test void recuperaYCambiaPasswordConValidaciones() {
        when(repository.findByEmail("ana@x.com")).thenReturn(Optional.of(user)); when(otp.generarPasswordLegible(12)).thenReturn("Temporal9"); when(hasher.hash(anyString())).thenReturn("hash2");
        service.resetearPasswordYNotificarPorCorreo(" ana@x.com "); verify(repository).updatePasswordByEmail("ana@x.com","hash2"); verify(otp).enviarPasswordTemporalRecuperacion("ana@x.com","ana","Temporal9");
        assertThrows(ResponseStatusException.class,()->service.resetearPasswordYNotificarPorCorreo(" "));
        when(repository.findById(7)).thenReturn(Optional.of(user)); when(hasher.matches("actual","hash")).thenReturn(true); when(hasher.matches("diferente","hash")).thenReturn(false);
        service.cambiarPasswordSesion(7,"actual","diferente"); verify(repository,times(2)).updatePasswordByEmail("ana@x.com","hash2");
        assertThrows(ResponseStatusException.class,()->service.cambiarPasswordSesion(7,"actual","corta"));
    }

    @Test void cambiaPasswordConTokenYLoInvalidaAlFinal() {
        when(otp.peekTokenCambioPassword("token")).thenReturn("ana@x.com"); when(repository.findByEmail("ana@x.com")).thenReturn(Optional.of(user));
        when(hasher.matches("actual","hash")).thenReturn(true); when(hasher.matches("diferente","hash")).thenReturn(false); when(hasher.hash("diferente")).thenReturn("hash2");
        service.cambiarPasswordConToken("token","actual","diferente"); verify(repository).updatePasswordByEmail("ana@x.com","hash2"); verify(otp).invalidateTokenCambioPassword("token");
        assertThrows(ResponseStatusException.class,()->service.cambiarPasswordConToken(" ","actual","diferente"));
    }

    private UsuarioDTO dto(short rol,String password){var d=new UsuarioDTO(); d.setNombre("Ana");d.setCedula("091");d.setCorreo("ana@x.com");d.setTelefono("099");d.setUsuario("ana");d.setIdRol(rol);d.setContrasena(password);return d;}
}
