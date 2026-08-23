package com.tiendatech.usuarios.domain.model;

/** Agregado de usuario independiente de persistencia y transporte. */
public record Usuario(Integer usuarioId, String nombre, String cedula, String correo,
                      String telefono, String usuario, String contrasenia, Boolean habilitado,
                      Short idMetodoPago, Short idRol, String avatarPath) {
    public Integer getUsuarioId() { return usuarioId; }
    public String getNombre() { return nombre; }
    public String getCedula() { return cedula; }
    public String getCorreo() { return correo; }
    public String getTelefono() { return telefono; }
    public String getUsuario() { return usuario; }
    public String getContrasenia() { return contrasenia; }
    public Boolean getHabilitado() { return habilitado; }
    public Short getIdMetodoPago() { return idMetodoPago; }
    public Short getIdRol() { return idRol; }
    public String getAvatarPath() { return avatarPath; }
}
