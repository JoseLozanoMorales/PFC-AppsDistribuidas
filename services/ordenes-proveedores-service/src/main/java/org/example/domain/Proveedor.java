package org.example.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class Proveedor {

    private Integer proveedorId;

    @NotBlank(message = "El nombre es obligatorio")
    @Pattern(regexp = "^(?=.*[\\p{L}0-9]).+$", message = "El nombre no puede contener solo simbolos")
    private String nombre;

    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "\\d{13}", message = "El RUC debe tener exactamente 13 digitos numericos")
    private String ruc;
    private String contactoNombre;

    @Pattern(regexp = "^$|\\d{7,10}", message = "El telefono debe tener entre 7 y 10 digitos numericos")
    private String telefono;

    @Email(message = "El correo no tiene un formato valido")
    private String correo;
    private String direccion;
    private Boolean activo;

    public Proveedor() {
    }

    public Integer getProveedorId() {
        return proveedorId;
    }

    public void setProveedorId(Integer proveedorId) {
        this.proveedorId = proveedorId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getContactoNombre() {
        return contactoNombre;
    }

    public void setContactoNombre(String contactoNombre) {
        this.contactoNombre = contactoNombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}
