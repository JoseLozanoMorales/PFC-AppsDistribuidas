package com.tiendatech.ordenesproveedores.presentation.dto;

import com.tiendatech.ordenesproveedores.domain.DetalleOrdenCompra;

import java.time.LocalDate;
import java.util.List;

public record CrearOrdenCompraRequest(
        Integer proveedorId,
        LocalDate fechaEsperada,
        List<DetalleOrdenCompra> detalle
) {
}
