package com.tiendatech.ventas.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cabecera de la factura, mapea 1 a 1 con ventas.factura_encabezado. */
public class Factura {
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

    public Factura() {}

    public Factura(Integer facturaId, Integer ordenId, Integer usuarioId, LocalDate fechaEmision,
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

    public Integer getFacturaId() { return facturaId; }
    public void setFacturaId(Integer facturaId) { this.facturaId = facturaId; }

    public Integer getOrdenId() { return ordenId; }
    public void setOrdenId(Integer ordenId) { this.ordenId = ordenId; }

    public Integer getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Integer usuarioId) { this.usuarioId = usuarioId; }

    public LocalDate getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDate fechaEmision) { this.fechaEmision = fechaEmision; }

    public LocalDate getFechaOrden() { return fechaOrden; }
    public void setFechaOrden(LocalDate fechaOrden) { this.fechaOrden = fechaOrden; }

    public String getCedula() { return cedula; }
    public void setCedula(String cedula) { this.cedula = cedula; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getDireccionEntrega() { return direccionEntrega; }
    public void setDireccionEntrega(String direccionEntrega) { this.direccionEntrega = direccionEntrega; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
}
