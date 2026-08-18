package org.example.dto;

import org.example.model.FacturaDetalle;

import java.math.BigDecimal;

public class FacturaDetalleResponse {
    private Integer productoId;
    private String nombreProducto;
    private Integer cantidad;
    private BigDecimal precio;
    private BigDecimal subtotal;
    private BigDecimal iva;
    private BigDecimal total;

    public FacturaDetalleResponse() {}

    public FacturaDetalleResponse(Integer productoId, String nombreProducto, Integer cantidad,
                                  BigDecimal precio, BigDecimal subtotal, BigDecimal iva, BigDecimal total) {
        this.productoId = productoId;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precio = precio;
        this.subtotal = subtotal;
        this.iva = iva;
        this.total = total;
    }

    public static FacturaDetalleResponse from(FacturaDetalle d) {
        return new FacturaDetalleResponse(d.getProductoId(), d.getNombreProducto(), d.getCantidad(),
                d.getPrecio(), d.getSubtotal(), d.getIva(), d.getTotal());
    }

    public Integer getProductoId() { return productoId; }
    public String getNombreProducto() { return nombreProducto; }
    public Integer getCantidad() { return cantidad; }
    public BigDecimal getPrecio() { return precio; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getIva() { return iva; }
    public BigDecimal getTotal() { return total; }
}