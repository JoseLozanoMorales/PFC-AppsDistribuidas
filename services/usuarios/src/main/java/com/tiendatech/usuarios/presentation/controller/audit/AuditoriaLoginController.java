package com.tiendatech.usuarios.presentation.controller.audit;

import com.tiendatech.usuarios.application.dto.audit.LoginAuditoriaDTO;
import com.tiendatech.usuarios.application.service.audit.UsuarioAuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaLoginController {

    private final UsuarioAuditoriaService service;

    @GetMapping("/usuariosL")
    public List<String> usuariosAuditoria() {
        return service.listarUsuariosVisibles();
    }

    @GetMapping("/logins")
    public List<LoginAuditoriaDTO> logins(
            @RequestParam(required = false, defaultValue = "") String usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return service.buscarLogins(usuario, desde, hasta);
    }
}
