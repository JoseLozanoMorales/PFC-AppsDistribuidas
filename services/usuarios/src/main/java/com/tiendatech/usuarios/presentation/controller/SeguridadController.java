package com.tiendatech.usuarios.presentation.controller;

import com.tiendatech.usuarios.application.service.UsuarioService;
import com.tiendatech.usuarios.presentation.support.UserAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/seguridad")
@RequiredArgsConstructor
public class SeguridadController {

    private final UsuarioService usuarioService;
    private final UserAccessGuard accessGuard;

    public record ChangePwdReq(String actual, String nueva) {}
    public record ChangePwdTokenReq(String token, String actual, String nueva) {}

    @PostMapping("/cambiar-password")
    public ResponseEntity<?> cambiarPassword(@RequestBody ChangePwdReq body) {
        usuarioService.cambiarPasswordSesion(
                accessGuard.currentUserId(),
                body.actual(), body.nueva());
        return ResponseEntity.ok(Map.of("ok", true, "message", "Contraseña actualizada"));
    }

    @PostMapping("/cambiar-password-token")
    public ResponseEntity<?> cambiarPasswordToken(@RequestBody ChangePwdTokenReq body) {
        usuarioService.cambiarPasswordConToken(body.token(), body.actual(), body.nueva());
        return ResponseEntity.ok(Map.of("ok", true, "message", "Contraseña actualizada"));
    }
}
