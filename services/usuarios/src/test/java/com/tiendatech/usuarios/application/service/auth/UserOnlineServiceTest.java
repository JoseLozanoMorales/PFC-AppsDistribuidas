package com.tiendatech.usuarios.application.service.auth;
import com.tiendatech.usuarios.domain.port.out.OnlineUserPort;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class UserOnlineServiceTest {@Test void delegaConsulta(){var port=mock(OnlineUserPort.class);when(port.isOnline("ana")).thenReturn(true);assertTrue(new UserOnlineService(port).isOnline("ana"));}}
