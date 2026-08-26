package com.tiendatech.pedidos.domain;

public class TipoMetodoPago {
    private Integer tipoId;
    private String nombre;

    public TipoMetodoPago() {}

    public TipoMetodoPago(Integer tipoId, String nombre) {
        this.tipoId = tipoId;
        this.nombre = nombre;
    }

    public Integer getTipoId() { return tipoId; }
    public void setTipoId(Integer tipoId) { this.tipoId = tipoId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
