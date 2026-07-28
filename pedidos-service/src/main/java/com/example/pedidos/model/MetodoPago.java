package com.example.pedidos.model;

import java.time.LocalDate;

public class MetodoPago {
    private Integer metodopagoId;
    private String numeroMascara;
    private LocalDate fechaExpiracion;
    private Boolean habilitado;
    private Integer tipoId;
    private String tipoNombre;

    public MetodoPago() {}

    public MetodoPago(Integer metodopagoId, String numeroMascara, LocalDate fechaExpiracion,
                      Boolean habilitado, Integer tipoId, String tipoNombre) {
        this.metodopagoId = metodopagoId;
        this.numeroMascara = numeroMascara;
        this.fechaExpiracion = fechaExpiracion;
        this.habilitado = habilitado;
        this.tipoId = tipoId;
        this.tipoNombre = tipoNombre;
    }

    public Integer getMetodopagoId() { return metodopagoId; }
    public void setMetodopagoId(Integer metodopagoId) { this.metodopagoId = metodopagoId; }

    public String getNumeroMascara() { return numeroMascara; }
    public void setNumeroMascara(String numeroMascara) { this.numeroMascara = numeroMascara; }

    public LocalDate getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(LocalDate fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }

    public Boolean getHabilitado() { return habilitado; }
    public void setHabilitado(Boolean habilitado) { this.habilitado = habilitado; }

    public Integer getTipoId() { return tipoId; }
    public void setTipoId(Integer tipoId) { this.tipoId = tipoId; }

    public String getTipoNombre() { return tipoNombre; }
    public void setTipoNombre(String tipoNombre) { this.tipoNombre = tipoNombre; }
}