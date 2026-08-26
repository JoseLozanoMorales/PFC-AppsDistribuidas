package com.tiendatech.ordenesproveedores.presentation.dto;

import com.tiendatech.ordenesproveedores.domain.DetalleOrdenCompra;

import java.math.BigDecimal;

public class DetalleOrdenCompraResponseDTO {
    private final Integer detalleId;
    private final Integer productoId;
    private final Integer cantidadPedida;
    private final Integer cantidadRecibida;
    private final BigDecimal costoUnitario;
    private final BigDecimal subtotalLinea;

    public DetalleOrdenCompraResponseDTO(Integer detalleId, Integer productoId, Integer cantidadPedida,
                                         Integer cantidadRecibida, BigDecimal costoUnitario,
                                         BigDecimal subtotalLinea) {
        this.detalleId = detalleId;
        this.productoId = productoId;
        this.cantidadPedida = cantidadPedida;
        this.cantidadRecibida = cantidadRecibida;
        this.costoUnitario = costoUnitario;
        this.subtotalLinea = subtotalLinea;
    }

    public static DetalleOrdenCompraResponseDTO from(DetalleOrdenCompra d) {
        return new DetalleOrdenCompraResponseDTO(d.getDetalleId(), d.getProductoId(), d.getCantidadPedida(),
                d.getCantidadRecibida(), d.getCostoUnitario(), d.getSubtotalLinea());
    }

    public Integer getDetalleId() { return detalleId; }
    public Integer getProductoId() { return productoId; }
    public Integer getCantidadPedida() { return cantidadPedida; }
    public Integer getCantidadRecibida() { return cantidadRecibida; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public BigDecimal getSubtotalLinea() { return subtotalLinea; }
}
