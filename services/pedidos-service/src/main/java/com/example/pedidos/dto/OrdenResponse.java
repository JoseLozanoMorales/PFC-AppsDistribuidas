package com.example.pedidos.dto;

import com.example.pedidos.model.Orden;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrdenResponse(
        Integer ordenId,
        Integer usuarioId,
        Integer direccionId,
        Integer metodopagoId,
        BigDecimal subtotal,
        BigDecimal total,
        LocalDate fecha) {

    public static OrdenResponse from(Orden orden) {
        return new OrdenResponse(
                orden.getOrdenId(),
                orden.getUsuarioId(),
                orden.getDireccionId(),
                orden.getMetodopagoId(),
                orden.getSubtotal(),
                orden.getTotal(),
                orden.getFecha());
    }
}
