package org.example.domain;

import java.math.BigDecimal;

public class DetalleOrden {
    private Integer ordenId;
    private Integer productoId;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
    private BigDecimal iva;
    private BigDecimal total;

    public DetalleOrden() {}

    public DetalleOrden(Integer ordenId, Integer productoId, Integer cantidad,
                        BigDecimal precioUnitario, BigDecimal subtotal, BigDecimal iva, BigDecimal total) {
        this.ordenId = ordenId;
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
        this.iva = iva;
        this.total = total;
    }

    public Integer getOrdenId() { return ordenId; }
    public void setOrdenId(Integer ordenId) { this.ordenId = ordenId; }

    public Integer getProductoId() { return productoId; }
    public void setProductoId(Integer productoId) { this.productoId = productoId; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getIva() { return iva; }
    public void setIva(BigDecimal iva) { this.iva = iva; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}
