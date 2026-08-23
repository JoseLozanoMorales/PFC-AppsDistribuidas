package org.example.presentation.dto;

import org.example.domain.Proveedor;

public class ProveedorResponseDTO {
    private final Integer proveedorId;
    private final String nombre;
    private final String ruc;
    private final String contactoNombre;
    private final String telefono;
    private final String correo;
    private final String direccion;
    private final Boolean activo;

    public ProveedorResponseDTO(Integer proveedorId, String nombre, String ruc, String contactoNombre,
                                String telefono, String correo, String direccion, Boolean activo) {
        this.proveedorId = proveedorId;
        this.nombre = nombre;
        this.ruc = ruc;
        this.contactoNombre = contactoNombre;
        this.telefono = telefono;
        this.correo = correo;
        this.direccion = direccion;
        this.activo = activo;
    }

    public static ProveedorResponseDTO from(Proveedor p) {
        return new ProveedorResponseDTO(p.getProveedorId(), p.getNombre(), p.getRuc(), p.getContactoNombre(),
                p.getTelefono(), p.getCorreo(), p.getDireccion(), p.getActivo());
    }

    public Integer getProveedorId() { return proveedorId; }
    public String getNombre() { return nombre; }
    public String getRuc() { return ruc; }
    public String getContactoNombre() { return contactoNombre; }
    public String getTelefono() { return telefono; }
    public String getCorreo() { return correo; }
    public String getDireccion() { return direccion; }
    public Boolean getActivo() { return activo; }
}
