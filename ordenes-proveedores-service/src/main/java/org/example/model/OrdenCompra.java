package org.example.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class OrdenCompra {

    private Integer ordenCompraId;
    private Integer proveedorId;
    private Integer usuarioId;
    private String numeroOrden;
    private LocalDate fechaEmision;
    private LocalDate fechaEsperada;
    private LocalDate fechaRecepcion;
    private EstadoOrdenCompra estado;
    private BigDecimal subtotal;
    private BigDecimal iva;
    private BigDecimal total;
    private BigDecimal subtotalPedido;
    private BigDecimal ivaPedido;
    private BigDecimal totalPedido;
    private List<DetalleOrdenCompra> detalle;

    public OrdenCompra() {
    }

    public Integer getOrdenCompraId() {
        return ordenCompraId;
    }

    public void setOrdenCompraId(Integer ordenCompraId) {
        this.ordenCompraId = ordenCompraId;
    }

    public Integer getProveedorId() {
        return proveedorId;
    }

    public void setProveedorId(Integer proveedorId) {
        this.proveedorId = proveedorId;
    }

    public Integer getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Integer usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getNumeroOrden() {
        return numeroOrden;
    }

    public void setNumeroOrden(String numeroOrden) {
        this.numeroOrden = numeroOrden;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(LocalDate fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public LocalDate getFechaEsperada() {
        return fechaEsperada;
    }

    public void setFechaEsperada(LocalDate fechaEsperada) {
        this.fechaEsperada = fechaEsperada;
    }

    public LocalDate getFechaRecepcion() {
        return fechaRecepcion;
    }

    public void setFechaRecepcion(LocalDate fechaRecepcion) {
        this.fechaRecepcion = fechaRecepcion;
    }

    public EstadoOrdenCompra getEstado() {
        return estado;
    }

    public void setEstado(EstadoOrdenCompra estado) {
        this.estado = estado;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getIva() {
        return iva;
    }

    public void setIva(BigDecimal iva) {
        this.iva = iva;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getSubtotalPedido() {
        return subtotalPedido;
    }

    public void setSubtotalPedido(BigDecimal subtotalPedido) {
        this.subtotalPedido = subtotalPedido;
    }

    public BigDecimal getIvaPedido() {
        return ivaPedido;
    }

    public void setIvaPedido(BigDecimal ivaPedido) {
        this.ivaPedido = ivaPedido;
    }

    public BigDecimal getTotalPedido() {
        return totalPedido;
    }

    public void setTotalPedido(BigDecimal totalPedido) {
        this.totalPedido = totalPedido;
    }

    public List<DetalleOrdenCompra> getDetalle() {
        return detalle;
    }

    public void setDetalle(List<DetalleOrdenCompra> detalle) {
        this.detalle = detalle;
    }
}