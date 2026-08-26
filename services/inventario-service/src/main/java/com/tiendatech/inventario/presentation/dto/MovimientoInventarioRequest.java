package com.tiendatech.inventario.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class MovimientoInventarioRequest {
    @NotNull(message = "producto_id es obligatorio")
    @JsonProperty("producto_id")
    private Integer productoId;

    @NotNull(message = "subtipo_id es obligatorio")
    @JsonProperty("subtipo_id")
    private Integer subtipoId;

    @NotNull(message = "cantidad es obligatoria")
    private Integer cantidad;

    @JsonProperty("costo_unitario")
    private BigDecimal costoUnitario;

    private String fecha;
    private String referencia;
    private String observacion;

    public Integer getProductoId() {
        return productoId;
    }

    public void setProductoId(Integer productoId) {
        this.productoId = productoId;
    }

    public Integer getSubtipoId() {
        return subtipoId;
    }

    public void setSubtipoId(Integer subtipoId) {
        this.subtipoId = subtipoId;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public void setCostoUnitario(BigDecimal costoUnitario) {
        this.costoUnitario = costoUnitario;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }
}
