package org.example.dto;

import org.example.model.EstadoOrdenCompra;
import org.example.model.OrdenCompra;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OrdenCompraResponseDTO {
    private final Integer ordenCompraId;
    private final Integer proveedorId;
    private final Integer usuarioId;
    private final String numeroOrden;
    private final LocalDate fechaEmision;
    private final LocalDate fechaEsperada;
    private final LocalDate fechaRecepcion;
    private final EstadoOrdenCompra estado;
    private final BigDecimal subtotal;
    private final BigDecimal iva;
    private final BigDecimal total;

    public OrdenCompraResponseDTO(Integer ordenCompraId, Integer proveedorId, Integer usuarioId,
                                  String numeroOrden, LocalDate fechaEmision, LocalDate fechaEsperada,
                                  LocalDate fechaRecepcion, EstadoOrdenCompra estado,
                                  BigDecimal subtotal, BigDecimal iva, BigDecimal total) {
        this.ordenCompraId = ordenCompraId;
        this.proveedorId = proveedorId;
        this.usuarioId = usuarioId;
        this.numeroOrden = numeroOrden;
        this.fechaEmision = fechaEmision;
        this.fechaEsperada = fechaEsperada;
        this.fechaRecepcion = fechaRecepcion;
        this.estado = estado;
        this.subtotal = subtotal;
        this.iva = iva;
        this.total = total;
    }

    public static OrdenCompraResponseDTO from(OrdenCompra o) {
        return new OrdenCompraResponseDTO(o.getOrdenCompraId(), o.getProveedorId(), o.getUsuarioId(),
                o.getNumeroOrden(), o.getFechaEmision(), o.getFechaEsperada(), o.getFechaRecepcion(),
                o.getEstado(), o.getSubtotal(), o.getIva(), o.getTotal());
    }

    public Integer getOrdenCompraId() { return ordenCompraId; }
    public Integer getProveedorId() { return proveedorId; }
    public Integer getUsuarioId() { return usuarioId; }
    public String getNumeroOrden() { return numeroOrden; }
    public LocalDate getFechaEmision() { return fechaEmision; }
    public LocalDate getFechaEsperada() { return fechaEsperada; }
    public LocalDate getFechaRecepcion() { return fechaRecepcion; }
    public EstadoOrdenCompra getEstado() { return estado; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getIva() { return iva; }
    public BigDecimal getTotal() { return total; }
}