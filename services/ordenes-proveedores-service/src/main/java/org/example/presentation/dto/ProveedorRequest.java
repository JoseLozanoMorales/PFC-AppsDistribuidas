package org.example.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.example.domain.Proveedor;

public record ProveedorRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Pattern(regexp = "^(?=.*[\\p{L}0-9]).+$", message = "El nombre no puede contener solo simbolos") String nombre,
        @NotBlank(message = "El RUC es obligatorio")
        @Pattern(regexp = "\\d{13}", message = "El RUC debe tener exactamente 13 digitos numericos") String ruc,
        String contactoNombre,
        @Pattern(regexp = "^$|\\d{7,10}", message = "El telefono debe tener entre 7 y 10 digitos numericos") String telefono,
        @Email(message = "El correo no tiene un formato valido") String correo,
        String direccion
) {
    public Proveedor toDomain() {
        Proveedor proveedor = new Proveedor();
        proveedor.setNombre(nombre);
        proveedor.setRuc(ruc);
        proveedor.setContactoNombre(contactoNombre);
        proveedor.setTelefono(telefono);
        proveedor.setCorreo(correo);
        proveedor.setDireccion(direccion);
        return proveedor;
    }
}
