package com.tiendatech.productos.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProductoResumenResponseTest {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void preservesCatalogJsonContract() {
        ProductoResumenResponse response = new ProductoResumenResponse(
                15L,
                "Producto de prueba",
                new BigDecimal("120.50"),
                null,
                LocalDate.of(2026, 8, 17),
                8,
                2L,
                3L,
                4L,
                new BigDecimal("90.00"),
                true,
                27L);

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("producto_id").asLong()).isEqualTo(15L);
        assertThat(json.get("preciounitario").decimalValue()).isEqualByComparingTo("120.50");
        assertThat(json.get("iva_id").asLong()).isEqualTo(4L);
        assertThat(json.get("stock").asInt()).isEqualTo(8);
        assertThat(json.get("habilitado").asBoolean()).isTrue();
        assertThat(json.get("galeria_id").asLong()).isEqualTo(27L);
        assertThat(json.has("productoId")).isFalse();
    }
}
