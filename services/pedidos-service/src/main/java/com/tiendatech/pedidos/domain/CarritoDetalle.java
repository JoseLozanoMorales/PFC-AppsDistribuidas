package com.tiendatech.pedidos.domain;

import java.math.BigDecimal;

public class CarritoDetalle {
    private Integer carritoId;
    private Integer productoId;
    private Integer cantidad;
    private BigDecimal precioUnitario;

    public CarritoDetalle() {}

    public CarritoDetalle(Integer carritoId, Integer productoId, Integer cantidad, BigDecimal precioUnitario) {
        this.carritoId = carritoId;
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }

    public Integer getCarritoId() { return carritoId; }
    public void setCarritoId(Integer carritoId) { this.carritoId = carritoId; }

    public Integer getProductoId() { return productoId; }
    public void setProductoId(Integer productoId) { this.productoId = productoId; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
}
