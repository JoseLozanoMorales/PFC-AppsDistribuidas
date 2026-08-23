package org.example.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Orden {
    private Integer ordenId;
    private Integer usuarioId;
    private Integer direccionId;
    private Integer metodopagoId;
    private BigDecimal subtotal;
    private BigDecimal total;
    private LocalDate fecha;

    public Orden() {}

    public Orden(Integer ordenId, Integer usuarioId, Integer direccionId,
                 Integer metodopagoId, BigDecimal subtotal, BigDecimal total, LocalDate fecha) {
        this.ordenId = ordenId;
        this.usuarioId = usuarioId;
        this.direccionId = direccionId;
        this.metodopagoId = metodopagoId;
        this.subtotal = subtotal;
        this.total = total;
        this.fecha = fecha;
    }

    public Integer getOrdenId() { return ordenId; }
    public void setOrdenId(Integer ordenId) { this.ordenId = ordenId; }

    public Integer getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Integer usuarioId) { this.usuarioId = usuarioId; }

    public Integer getDireccionId() { return direccionId; }
    public void setDireccionId(Integer direccionId) { this.direccionId = direccionId; }

    public Integer getMetodopagoId() { return metodopagoId; }
    public void setMetodopagoId(Integer metodopagoId) { this.metodopagoId = metodopagoId; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
}
