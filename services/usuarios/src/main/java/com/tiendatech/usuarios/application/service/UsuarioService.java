// src/main/java/com/example/tienda_tech/service/UsuarioService.java
package com.tiendatech.usuarios.application.service;

import com.tiendatech.usuarios.application.dto.UsuarioAdminDTO;
import com.tiendatech.usuarios.application.dto.UsuarioDTO;
import com.tiendatech.usuarios.application.dto.UsuarioMinDTO;
import com.tiendatech.usuarios.domain.port.out.UsuarioQueryPort;
import com.tiendatech.usuarios.domain.model.Usuario;
import com.tiendatech.usuarios.domain.model.AdminUserCommand;
import com.tiendatech.usuarios.domain.port.out.AdminUserPort;
import com.tiendatech.usuarios.domain.port.out.PasswordHasher;
import com.tiendatech.usuarios.domain.port.out.UsuarioRepositoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import com.tiendatech.usuarios.application.dto.ClienteUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepositoryPort usuarioRepository;
    private final PasswordHasher passwordHasher;

    // NUEVO: para enviar credenciales por correo antes de registrar
    private final OtpService otpService;
    // NUEVO
    private final UsuarioQueryPort usuarioQueryRepository;
    private final AdminUserPort adminUserPort;

    public Usuario getById(Integer id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new com.tiendatech.usuarios.domain.exception.NotFoundException("Usuario no encontrado"));
        // Si no tienes NotFoundException, usa:
        // .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }



    public Usuario login(String usuario, String contraseniaPlain) {
        var opt = usuarioRepository.findByUsername(usuario);
        if (opt.isEmpty()) throw new IllegalArgumentException("Credenciales inválidas");

        Usuario u = opt.get();
        String hash = u.getContrasenia(); // la columna actualmente se llama así
        if (hash == null || !passwordHasher.matches(contraseniaPlain, hash)) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }
        return u;
    }


    // Ya existente: registro público (cliente)
    @Transactional
    public Usuario crearClienteConSP(UsuarioDTO dto) {
        String raw = dto.getContrasena();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }
        String hash = passwordHasher.hash(raw);

        usuarioRepository.registerClient(
                dto.getNombre(),
                dto.getCedula(),
                dto.getCorreo(),
                dto.getTelefono(),
                dto.getUsuario(),
                hash                              // <<--- ENVIAR HASH
        );
        return usuarioRepository.findByUsername(dto.getUsuario())
                .orElseThrow(() -> new IllegalStateException("Usuario creado, pero no se pudo resolver su identificador"));
    }


    // Panel admin: crea usuarios con cualquier rol permitido.
    @Transactional
    public Usuario crearUsuarioConSP(UsuarioDTO dto) {
        Integer idMetodoPago = dto.getIdMetodoPago() != null ? dto.getIdMetodoPago().intValue() : null;
        Integer idRol        = dto.getIdRol() != null        ? dto.getIdRol().intValue()        : 2; // por defecto cliente

        usuarioRepository.createUser(
                dto.getNombre(),
                dto.getCedula(),
                dto.getCorreo(),
                dto.getTelefono(),
                dto.getContrasena(), // mapea a v_contrasenia
                dto.getUsuario(),
                idMetodoPago,
                idRol
        );
        return usuarioRepository.findByUsername(dto.getUsuario())
                .orElseThrow(() -> new IllegalStateException("Usuario creado, pero no se pudo resolver su identificador"));
    }
    public void actualizarCliente(Integer usuarioId, ClienteUpdateRequest req) {
        String nombre    = emptyToNull(req.getNombre());
        String cedula    = emptyToNull(req.getCedula());
        String correo    = emptyToNull(req.getCorreo());
        String telefono  = emptyToNull(req.getTelefono());
        String usuario   = emptyToNull(req.getUsuario());
        String contrasenia = emptyToNull(req.getContrasena());

        // Si el front manda nueva contraseña, hasheamos
        if (contrasenia != null) {
            contrasenia = passwordHasher.hash(contrasenia);
        }

        usuarioRepository.updateClient(
                usuarioId, nombre, cedula, correo, telefono, usuario, contrasenia
        );
    }

    /* ======== NUEVO (admin/trabajador) por JSON con envío de credenciales ======== */
    @Transactional
    public Usuario crearAdminOTrabajador(UsuarioDTO dto) {
        if (dto.getIdRol() == null || (dto.getIdRol() != 1 && dto.getIdRol() != 3)) {
            throw new IllegalArgumentException("idRol debe ser 1 (admin) o 3 (trabajador)");
        }
        if (dto.getCorreo() == null || dto.getCorreo().isBlank()) {
            throw new IllegalArgumentException("El correo es obligatorio para enviar credenciales");
        }

        // 1) Generar y ENVIAR por correo (si falla -> excepción -> NO se registra)
        String passwordPlano = emptyToNull(dto.getContrasena());
        if (passwordPlano == null) {
            passwordPlano = otpService.generarYEnviarCredenciales(
                    dto.getCorreo(), dto.getUsuario(), 12);
        }

        // 2) Hashear para BD
        String hash = passwordHasher.hash(passwordPlano);

        // 3) Construir item JSON para SP
        UsuarioAdminDTO a = UsuarioAdminDTO.builder()
                .accion("AGREGAR")
                .nombre(dto.getNombre())
                .cedula(dto.getCedula())
                .correo(dto.getCorreo())
                .telefono(dto.getTelefono())
                .usuario(dto.getUsuario())
                .contrasenia(hash) // HASH al SP JSON
                .rolId(dto.getIdRol().intValue())
                .build();

        // 4) Enviar al SP (procedure o function con fallback)
        gestionarAdmins(List.of(a));
        return usuarioRepository.findByUsername(dto.getUsuario())
                .orElseThrow(() -> new IllegalStateException("Usuario creado, pero no se pudo resolver su identificador"));
    }

    // NUEVO: buscar con la función ligera
    @Transactional(readOnly = true)
    public List<UsuarioMinDTO> buscarMin(String q, Integer rolId, int limit) {
        return usuarioQueryRepository.search(q, rolId, limit).stream()
                .map(item -> new UsuarioMinDTO(item.id(), item.nombre(), item.cedula(), item.correo(),
                        item.telefono(), item.usuario(), item.rolId(), item.habilitado()))
                .toList();
    }

    @Transactional
    public void actualizarAdmin(Integer id, Integer rolId, ClienteUpdateRequest req) {
        String contrasenia = emptyToNull(req.getContrasena());
        if (contrasenia != null) contrasenia = passwordHasher.hash(contrasenia);

        UsuarioAdminDTO a = UsuarioAdminDTO.builder()
                .accion("ACTUALIZAR")
                .usuarioId(id)
                .rolId(rolId)
                .nombre(emptyToNull(req.getNombre()))
                .cedula(emptyToNull(req.getCedula()))
                .correo(emptyToNull(req.getCorreo()))
                .telefono(emptyToNull(req.getTelefono()))
                .usuario(emptyToNull(req.getUsuario()))
                .contrasenia(contrasenia) // null => no cambia
                .build();

        gestionarAdmins(List.of(a));
    }

    @Transactional
    public void deshabilitarAdmin(Integer id, Integer rolId) {
        UsuarioAdminDTO a = UsuarioAdminDTO.builder()
                .accion("DESHABILITAR")
                .usuarioId(id)
                .rolId(rolId)
                .build();

        gestionarAdmins(List.of(a));
    }

    /*
     Envía el lote al SP/función JSON con fallback:
     Primero intenta CALL (procedure)
     Si falla, intenta SELECT (function)
     */
    @Transactional
    public void gestionarAdmins(List<UsuarioAdminDTO> items) {
        adminUserPort.execute(items.stream().map(a -> new AdminUserCommand(a.getAccion(), a.getUsuarioId(),
                a.getRolId(), a.getNombre(), a.getCedula(), a.getCorreo(), a.getTelefono(), a.getUsuario(),
                a.getContrasenia())).toList());
    }

    /* ======== BÚSQUEDA (sin searchMin) ======== */
    @Transactional(readOnly = true)
    public List<Usuario> buscarPorUsuario(String usuario, Integer rolId, int limit) {
        String q = usuario == null ? "" : usuario.trim();
        int lim = Math.max(1, Math.min(limit <= 0 ? 10 : limit, 50)); // 1..50

        List<Usuario> lista = usuarioRepository.searchByUsername(q, rolId);

        return lista.size() > lim ? lista.subList(0, lim) : lista;
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetearPasswordYNotificarPorCorreo(String correo) {
        String c = (correo == null ? "" : correo.trim());
        if (c.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo es requerido");
        }

        // 1) Validar existencia (para no “crear” ni ocultar errores del SP)
        Usuario u = usuarioRepository.findByEmail(c)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el correo"));

        // 2) Generar password temporal legible + hashear (BCrypt)
        String plain = otpService.generarPasswordLegible(12);
        String hash  = passwordHasher.hash(plain);

        // 3) Actualizar en BD con tu PROCEDURE (CALL) usando JSONB
        usuarioRepository.updatePasswordByEmail(c, hash);

        // 4) Enviar el correo (si falla => excepción => rollback de la TX => NO se cambia la clave)
        otpService.enviarPasswordTemporalRecuperacion(u.getCorreo(), u.getUsuario(), plain);
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }


    @org.springframework.transaction.annotation.Transactional
    public void cambiarPasswordSesion(Integer userId, String actual, String nueva) {
        if (nueva == null || nueva.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener al menos 8 caracteres");
        }

        Usuario u = getById(userId);

        String hashActual = u.getContrasenia();
        if (hashActual == null || !passwordHasher.matches(actual, hashActual)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        if (passwordHasher.matches(nueva, hashActual)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña no puede ser igual a la actual");
        }

        String nuevoHash = passwordHasher.hash(nueva);
        // === TU SP por correo ===
        usuarioRepository.updatePasswordByEmail(u.getCorreo(), nuevoHash);
    }

    @org.springframework.transaction.annotation.Transactional
    public void cambiarPasswordConToken(String token, String actual, String nueva) {
        if (token == null || token.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token requerido");
        if (actual == null || actual.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa tu contraseña actual");
        if (nueva == null || nueva.length() < 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener al menos 8 caracteres");

        // 1) Obtener correo desde el token (sin invalidar aún)
        String correo = otpService.peekTokenCambioPassword(token);
        if (correo == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token inválido o expirado");

        // 2) Traer usuario y validar "actual"
        Usuario u = usuarioRepository.findByEmail(correo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        String hashActual = u.getContrasenia();
        if (hashActual == null || !passwordHasher.matches(actual, hashActual))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        if (passwordHasher.matches(nueva, hashActual))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña no puede ser igual a la actual");

        // 3) Guardar nueva (tu SP)
        String nuevoHash = passwordHasher.hash(nueva);
        usuarioRepository.updatePasswordByEmail(correo, nuevoHash);

        // 4) Invalidar el token SOLO si todo salió ok
        otpService.invalidateTokenCambioPassword(token);
    }
}
