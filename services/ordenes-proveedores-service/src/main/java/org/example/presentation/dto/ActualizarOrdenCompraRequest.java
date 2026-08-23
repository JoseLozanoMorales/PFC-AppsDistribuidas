package org.example.presentation.dto;

import org.example.domain.DetalleOrdenCompra;

import java.time.LocalDate;
import java.util.List;

public record ActualizarOrdenCompraRequest(
        Integer proveedorId,
        LocalDate fechaEsperada,
        List<DetalleOrdenCompra> detalle
) {
}
