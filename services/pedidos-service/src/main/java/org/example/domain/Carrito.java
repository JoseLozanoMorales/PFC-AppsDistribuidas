package org.example.domain;

import java.math.BigDecimal;

public class Carrito {
    private Integer carritoId;
    private Integer usuarioId;
    private BigDecimal total;
    private Boolean habilitado;

    public Carrito() {}

    public Carrito(Integer carritoId, Integer usuarioId, BigDecimal total, Boolean habilitado) {
        this.carritoId = carritoId;
        this.usuarioId = usuarioId;
        this.total = total;
        this.habilitado = habilitado;
    }

    public Integer getCarritoId() { return carritoId; }
    public void setCarritoId(Integer carritoId) { this.carritoId = carritoId; }

    public Integer getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Integer usuarioId) { this.usuarioId = usuarioId; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public Boolean getHabilitado() { return habilitado; }
    public void setHabilitado(Boolean habilitado) { this.habilitado = habilitado; }
}
