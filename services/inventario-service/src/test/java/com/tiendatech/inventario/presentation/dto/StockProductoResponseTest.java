package com.tiendatech.inventario.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockProductoResponseTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preservesExistingJsonContract() throws Exception {
        JsonNode json = objectMapper.valueToTree(
                new StockProductoResponse(15L, "Producto de prueba", 8));

        assertThat(json.get("producto_id").asLong()).isEqualTo(15L);
        assertThat(json.get("nombre").asText()).isEqualTo("Producto de prueba");
        assertThat(json.get("stock").asInt()).isEqualTo(8);
        assertThat(json.has("productoId")).isFalse();
    }
}
