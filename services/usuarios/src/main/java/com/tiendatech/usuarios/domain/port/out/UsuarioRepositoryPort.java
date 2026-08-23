package com.tiendatech.usuarios.domain.port.out;

import com.tiendatech.usuarios.domain.model.Usuario;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepositoryPort {
    Optional<Usuario> findById(Integer id);
    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByEmail(String email);
    List<Usuario> searchByUsername(String username, Integer roleId);
    void registerClient(String nombre, String cedula, String correo, String telefono, String usuario, String passwordHash);
    void createUser(String nombre, String cedula, String correo, String telefono, String passwordHash,
                    String usuario, Integer paymentMethodId, Integer roleId);
    void updateClient(Integer id, String nombre, String cedula, String correo, String telefono,
                      String usuario, String passwordHash);
    void updatePasswordByEmail(String email, String passwordHash);
}
