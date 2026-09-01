package com.tiendatech.ventas.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Instantánea inmutable recibida de Pedidos; Ventas no consulta esquemas ajenos. */
public record FacturaDraft(Integer ordenId, LocalDate fechaOrden, Integer usuarioId,
                           BigDecimal subtotal, BigDecimal total, List<Linea> lineas) {
    public record Linea(Integer productoId, Integer cantidad, BigDecimal precio,
                        BigDecimal subtotal, BigDecimal iva, BigDecimal total) {}
}
