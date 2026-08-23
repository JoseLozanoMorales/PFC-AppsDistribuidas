package org.example.domain;

import java.math.BigDecimal;

public record ProductoInfo(Integer productoId, BigDecimal precioUnitario,
                           Integer ivaId, BigDecimal porcentajeIva) {
}
