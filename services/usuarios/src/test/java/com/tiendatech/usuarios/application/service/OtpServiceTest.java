package com.tiendatech.usuarios.application.service;

import com.tiendatech.usuarios.domain.port.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OtpServiceTest {
    private final EmailPort email=mock(EmailPort.class); private final OtpStorePort store=mock(OtpStorePort.class);
    private final OtpService service=new OtpService(email,store);
    @BeforeEach void config(){ReflectionTestUtils.setField(service,"otpTtlMin",10);ReflectionTestUtils.setField(service,"otpLen",6);ReflectionTestUtils.setField(service,"resendCooldownSeconds",60);ReflectionTestUtils.setField(service,"maxPerWindow",3);ReflectionTestUtils.setField(service,"mailFallbackEnabled",false);ReflectionTestUtils.setField(service,"frontendBaseUrl","http://localhost/");}

    @Test void enviaYValidaOtpDeUnSoloUso(){when(store.findAttemptCount(anyString())).thenReturn(0); Map<String,Object> result=service.enviar("ana@x.com","tx-1"); assertEquals("tx-1",result.get("txId"));assertEquals(true,result.get("mailSent"));verify(store).saveOtpHash(eq("ana@x.com:tx-1"),anyString());verify(email).send(eq("ana@x.com"),anyString(),anyString());}
    @Test void limitaReenvios(){when(store.findAttemptCount(anyString())).thenReturn(3);assertThrows(OtpService.OtpTooManyRequestsException.class,()->service.enviar("ana@x.com",null));}
    @Test void eliminaOtpSiFallaCorreo(){when(store.findAttemptCount(anyString())).thenReturn(0);doThrow(new RuntimeException("smtp")).when(email).send(anyString(),anyString(),anyString());assertThrows(RuntimeException.class,()->service.enviar("ana@x.com","tx"));verify(store).removeOtp("ana@x.com:tx");}
    @Test void permiteFallbackLocal(){ReflectionTestUtils.setField(service,"mailFallbackEnabled",true);when(store.findAttemptCount(anyString())).thenReturn(null);doThrow(new RuntimeException("smtp")).when(email).send(anyString(),anyString(),anyString());var r=service.enviar("ana@x.com","tx");assertEquals(false,r.get("mailSent"));assertNotNull(r.get("devCode"));}
    @Test void validaHashYConsumeOtp(){String hash=org.springframework.security.crypto.bcrypt.BCrypt.hashpw("123456",org.springframework.security.crypto.bcrypt.BCrypt.gensalt());when(store.findOtpHash("ana@x.com:tx")).thenReturn(hash);assertTrue(service.validar("ana@x.com","123456","tx"));verify(store).removeOtp("ana@x.com:tx");assertFalse(service.validar("otro@x.com","123456","tx"));}
    @Test void generaPasswordYAdministraToken(){String password=service.generarPasswordLegible(4);assertEquals(8,password.length());assertTrue(password.chars().anyMatch(Character::isUpperCase));assertTrue(password.chars().anyMatch(Character::isLowerCase));assertTrue(password.chars().anyMatch(Character::isDigit));String token=service.emitirTokenCambioPassword("ANA@X.COM");verify(store).savePasswordChangeToken(token,"ana@x.com");when(store.findPasswordChangeEmail(token)).thenReturn("ana@x.com");assertEquals("ana@x.com",service.peekTokenCambioPassword(token));service.invalidateTokenCambioPassword(token);verify(store).removePasswordChangeToken(token);}
    @Test void enviaCredencialesConUrlNormalizada(){service.enviarCredenciales("ana@x.com","ana","Temp9abc");verify(email).send(eq("ana@x.com"),contains("credenciales"),contains("http://localhost/ActualizarContrasenia.html?t="));}
}
