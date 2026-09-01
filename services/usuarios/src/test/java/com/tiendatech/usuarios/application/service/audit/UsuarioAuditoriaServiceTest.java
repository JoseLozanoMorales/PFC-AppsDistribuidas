package com.tiendatech.usuarios.application.service.audit;

import com.tiendatech.usuarios.domain.model.LoginAuditoria;
import com.tiendatech.usuarios.domain.port.out.LoginAuditPort;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UsuarioAuditoriaServiceTest {
    private final LoginAuditPort repo = mock(LoginAuditPort.class);
    private final UsuarioAuditoriaService service = new UsuarioAuditoriaService(repo);

    @Test void registrarLoginDelegaAlRepositorio() {
        service.registrarLogin(7);
        verify(repo).register(7);
    }

    @Test void listarUsuariosVisiblesDelegaAlRepositorio() {
        when(repo.visibleUsers()).thenReturn(List.of("ana", "luis"));
        assertEquals(List.of("ana", "luis"), service.listarUsuariosVisibles());
    }

    @Test void buscarLoginsNormalizaUsuarioYMapeaDto() {
        OffsetDateTime fecha = OffsetDateTime.now();
        when(repo.search("ana", null, null))
                .thenReturn(List.of(new LoginAuditoria(1, 5, "ana", "127.0.0.1", "host1", fecha)));
        var resultado = service.buscarLogins("  ana  ", null, null);
        assertEquals(1, resultado.size());
        assertEquals("ana", resultado.get(0).getUsuario());
        assertEquals("127.0.0.1", resultado.get(0).getIp());
    }

    @Test void buscarLoginsUsaCadenaVaciaSiUsuarioEsNuloOblanco() {
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 31);
        when(repo.search("", desde, hasta)).thenReturn(List.of());
        assertTrue(service.buscarLogins("   ", desde, hasta).isEmpty());
        verify(repo).search("", desde, hasta);
    }
}
