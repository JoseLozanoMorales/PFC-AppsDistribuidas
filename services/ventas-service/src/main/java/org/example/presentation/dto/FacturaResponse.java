package org.example.presentation.dto;

import org.example.domain.Factura;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FacturaResponse {
    private Integer facturaId;
    private Integer ordenId;
    private Integer usuarioId;
    private LocalDate fechaEmision;
    private LocalDate fechaOrden;
    private String cedula;
    private String nombre;
    private String correo;
    private String telefono;
    private String direccionEntrega;
    private BigDecimal subtotal;
    private BigDecimal total;
    private String numero;

    public FacturaResponse() {}

    public FacturaResponse(Integer facturaId, Integer ordenId, Integer usuarioId, LocalDate fechaEmision,
                           LocalDate fechaOrden, String cedula, String nombre, String correo, String telefono,
                           String direccionEntrega, BigDecimal subtotal, BigDecimal total, String numero) {
        this.facturaId = facturaId;
        this.ordenId = ordenId;
        this.usuarioId = usuarioId;
        this.fechaEmision = fechaEmision;
        this.fechaOrden = fechaOrden;
        this.cedula = cedula;
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
        this.direccionEntrega = direccionEntrega;
        this.subtotal = subtotal;
        this.total = total;
        this.numero = numero;
    }

    public static FacturaResponse from(Factura f) {
        return new FacturaResponse(f.getFacturaId(), f.getOrdenId(), f.getUsuarioId(), f.getFechaEmision(),
                f.getFechaOrden(), f.getCedula(), f.getNombre(), f.getCorreo(), f.getTelefono(),
                f.getDireccionEntrega(), f.getSubtotal(), f.getTotal(), f.getNumero());
    }

    public Integer getFacturaId() { return facturaId; }
    public Integer getOrdenId() { return ordenId; }
    public Integer getUsuarioId() { return usuarioId; }
    public LocalDate getFechaEmision() { return fechaEmision; }
    public LocalDate getFechaOrden() { return fechaOrden; }
    public String getCedula() { return cedula; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public String getTelefono() { return telefono; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTotal() { return total; }
    public String getNumero() { return numero; }
}
