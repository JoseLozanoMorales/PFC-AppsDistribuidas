// src/main/java/com/example/tienda_tech/controller/LoginController.java
package com.tiendatech.usuarios.controller;

import com.tiendatech.usuarios.model.Usuario;
import com.tiendatech.usuarios.security.JwtUtil;
import com.tiendatech.usuarios.service.UsuarioService;
import com.tiendatech.usuarios.service.audit.UsuarioAuditoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoginController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioAuditoriaService usuarioAuditoriaService;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${auth.access.minutes:10}")
    private int accessMinutes;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String usuario = body.get("usuario");
        String contrasenia = body.get("contrasena");

        var u = usuarioService.login(usuario, contrasenia);

        int rol = (u.getIdRol() == null) ? 0 : u.getIdRol();
        if (rol == 1 || rol == 3) {

           usuarioAuditoriaService.registrarLogin(u.getUsuarioId());

        }

        Map<String, Object> userPayload = Map.of(
                "usuarioId", u.getUsuarioId(),
                "usuario",   u.getUsuario(),
                "nombre",    u.getNombre(),
                "cedula",    u.getCedula(),
                "correo",    u.getCorreo(),
                "telefono",  u.getTelefono(),
                "id_rol",    u.getIdRol()
        );
        String accessToken = jwtUtil.generateAccess(
                u.getUsuarioId(),
                u.getUsuario(),
                roleName(rol),
                accessMinutes
        );
        return ResponseEntity.ok(Map.of(
                "success", true,
                "user", userPayload,
                "token", accessToken,
                "access", accessToken
        ));
    }

    private String roleName(int rol) {
        return switch (rol) {
            case 1 -> "ADMIN";
            case 2 -> "CLIENTE";
            case 3 -> "TRABAJADOR";
            default -> "UNKNOWN";
        };
    }

}
