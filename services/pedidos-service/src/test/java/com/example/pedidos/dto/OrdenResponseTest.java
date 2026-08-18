package com.example.pedidos.dto;

import com.example.pedidos.model.DetalleOrden;
import com.example.pedidos.model.Orden;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OrdenResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void convierteOrdenSinExponerLaClaseDePersistencia() throws Exception {
        Orden orden = new Orden(21, 8, 3, 2,
                new BigDecimal("100.00"), new BigDecimal("115.00"), LocalDate.of(2026, 8, 18));

        OrdenResponse response = OrdenResponse.from(orden);
        String json = objectMapper.writeValueAsString(response);

        assertThat(response).isNotInstanceOf(Orden.class);
        assertThat(json).contains("\"ordenId\":21", "\"usuarioId\":8", "\"total\":115.00");
    }

    @Test
    void convierteDetalleSinExponerLaClaseDePersistencia() throws Exception {
        DetalleOrden detalle = new DetalleOrden(21, 9, 2,
                new BigDecimal("50.00"), new BigDecimal("100.00"),
                new BigDecimal("15.00"), new BigDecimal("115.00"));

        DetalleOrdenResponse response = DetalleOrdenResponse.from(detalle);
        String json = objectMapper.writeValueAsString(response);

        assertThat(response).isNotInstanceOf(DetalleOrden.class);
        assertThat(json).contains("\"productoId\":9", "\"precioUnitario\":50.00", "\"iva\":15.00");
    }
}
