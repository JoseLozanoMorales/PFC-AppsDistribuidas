package com.tiendatech.usuarios.infrastructure.persistence.adapter;

import com.tiendatech.usuarios.domain.model.AdminUserCommand;
import com.tiendatech.usuarios.domain.port.out.AdminUserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JdbcAdminUserAdapter implements AdminUserPort {
    private final JdbcTemplate jdbc;

    @Override
    public void execute(List<AdminUserCommand> commands) {
        for (AdminUserCommand command : commands) {
            switch (command.action().toUpperCase()) {
                case "AGREGAR" -> jdbc.update(
                        "INSERT INTO usuarios.usuario (nombre,cedula,correo,telefono,usuario,contrasenia,rol_id,habilitado) VALUES (?,?,?,?,?,?,?,true)",
                        command.nombre(), command.cedula(), command.correo(), command.telefono(), command.username(),
                        command.passwordHash(), command.roleId());
                case "ACTUALIZAR" -> jdbc.update(
                        "UPDATE usuarios.usuario SET nombre=coalesce(?,nombre),cedula=coalesce(?,cedula),correo=coalesce(?,correo),telefono=coalesce(?,telefono),usuario=coalesce(?,usuario),contrasenia=coalesce(?,contrasenia),rol_id=coalesce(?,rol_id) WHERE usuario_id=?",
                        command.nombre(), command.cedula(), command.correo(), command.telefono(), command.username(),
                        command.passwordHash(), command.roleId(), command.id());
                case "DESHABILITAR" -> jdbc.update(
                        "UPDATE usuarios.usuario SET habilitado=false WHERE usuario_id=? AND rol_id=?",
                        command.id(), command.roleId());
                default -> throw new IllegalArgumentException("Acción administrativa inválida: " + command.action());
            }
        }
    }
}
