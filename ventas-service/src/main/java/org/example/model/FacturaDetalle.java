package org.example.model;

import java.math.BigDecimal;

/** Linea de la factura, mapea 1 a 1 con ventas.factura_cuerpo. */
public class FacturaDetalle {
    private Integer facturaId;
    private Integer productoId;
    private String nombreProducto;
    private Integer cantidad;
    private BigDecimal precio;
    private BigDecimal subtotal;
    private BigDecimal iva;
    private BigDecimal total;

    public FacturaDetalle() {}

    public FacturaDetalle(Integer facturaId, Integer productoId, String nombreProducto, Integer cantidad,
                          BigDecimal precio, BigDecimal subtotal, BigDecimal iva, BigDecimal total) {
        this.facturaId = facturaId;
        this.productoId = productoId;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precio = precio;
        this.subtotal = subtotal;
        this.iva = iva;
        this.total = total;
    }

    public Integer getFacturaId() { return facturaId; }
    public void setFacturaId(Integer facturaId) { this.facturaId = facturaId; }

    public Integer getProductoId() { return productoId; }
    public void setProductoId(Integer productoId) { this.productoId = productoId; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getIva() { return iva; }
    public void setIva(BigDecimal iva) { this.iva = iva; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}