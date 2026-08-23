package com.tiendatech.usuarios.infrastructure.persistence.adapter;

import com.tiendatech.usuarios.domain.model.Usuario;
import com.tiendatech.usuarios.domain.port.out.UsuarioRepositoryPort;
import com.tiendatech.usuarios.infrastructure.persistence.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UsuarioRepositoryAdapter implements UsuarioRepositoryPort {
    private final UsuarioRepository repository;

    @Override public Optional<Usuario> findById(Integer id) { return repository.findById(id).map(this::toDomain); }
    @Override public Optional<Usuario> findByUsername(String username) { return repository.findByUsuario(username).map(this::toDomain); }
    @Override public Optional<Usuario> findByEmail(String email) { return repository.findByCorreoIgnoreCase(email).map(this::toDomain); }

    @Override
    public List<Usuario> searchByUsername(String username, Integer roleId) {
        var entities = roleId == null
                ? repository.findTop50ByUsuarioContainingIgnoreCase(username)
                : repository.findTop50ByUsuarioContainingIgnoreCaseAndIdRol(username, roleId);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public void registerClient(String nombre, String cedula, String correo, String telefono, String usuario, String hash) {
        repository.registrarClienteSP(nombre, cedula, correo, telefono, usuario, hash);
    }

    @Override
    public void createUser(String nombre, String cedula, String correo, String telefono, String hash,
                           String usuario, Integer paymentMethodId, Integer roleId) {
        repository.crearUsuarioSP(nombre, cedula, correo, telefono, hash, usuario, paymentMethodId, roleId);
    }

    @Override
    public void updateClient(Integer id, String nombre, String cedula, String correo, String telefono,
                             String usuario, String hash) {
        repository.actualizarClienteSP(id, nombre, cedula, correo, telefono, usuario, hash);
    }

    @Override public void updatePasswordByEmail(String email, String hash) {
        repository.actualizarContraseniaPorCorreoCall(email, hash);
    }

    private Usuario toDomain(com.tiendatech.usuarios.infrastructure.persistence.entity.Usuario entity) {
        return new Usuario(entity.getUsuarioId(), entity.getNombre(), entity.getCedula(), entity.getCorreo(),
                entity.getTelefono(), entity.getUsuario(), entity.getContrasenia(), entity.getHabilitado(),
                entity.getIdMetodoPago(), entity.getIdRol(), entity.getAvatarPath());
    }
}
