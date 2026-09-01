package com.tiendatech.ventas.presentation.dto;

import com.tiendatech.ventas.domain.FacturaDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GenerarFacturaRequest(
        @NotNull @Positive Integer ordenId,
        @NotNull LocalDate fechaOrden,
        @NotNull @Positive Integer usuarioId,
        @NotNull @PositiveOrZero BigDecimal subtotal,
        @NotNull @PositiveOrZero BigDecimal total,
        @NotEmpty List<@Valid Linea> lineas) {

    public record Linea(@NotNull @Positive Integer productoId,
                        @NotNull @Positive Integer cantidad,
                        @NotNull @PositiveOrZero BigDecimal precio,
                        @NotNull @PositiveOrZero BigDecimal subtotal,
                        @NotNull @PositiveOrZero BigDecimal iva,
                        @NotNull @PositiveOrZero BigDecimal total) {}

    public FacturaDraft toDomain() {
        return new FacturaDraft(ordenId, fechaOrden, usuarioId, subtotal, total,
                lineas.stream().map(linea -> new FacturaDraft.Linea(
                        linea.productoId, linea.cantidad, linea.precio,
                        linea.subtotal, linea.iva, linea.total)).toList());
    }
}
