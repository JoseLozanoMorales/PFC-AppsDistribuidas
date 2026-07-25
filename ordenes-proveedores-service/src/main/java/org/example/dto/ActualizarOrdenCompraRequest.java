package org.example.dto;
import org.example.model.DetalleOrdenCompra;

import java.time.LocalDate;
import java.util.List;

public record ActualizarOrdenCompraRequest(
        Integer proveedorId,
        LocalDate fechaEsperada,
        List<DetalleOrdenCompra> detalle
) {
}
