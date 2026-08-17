package com.example.productos.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CrearProductoRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiresNameAndPositiveCategory() {
        CrearProductoRequest request = new CrearProductoRequest();
        request.setCategoriaId(0);

        assertThat(validator.validate(request))
                .extracting(error -> error.getPropertyPath().toString())
                .containsExactlyInAnyOrder("nombre", "categoriaId");
    }

    @Test
    void preservesDynamicCategoryAttributes() throws Exception {
        CrearProductoRequest request = objectMapper.readValue("""
                {
                  "nombre": "Procesador de prueba",
                  "categoria_id": 2,
                  "preciounitario": 100.50,
                  "nucleos": 8,
                  "socket": "AM5"
                }
                """, CrearProductoRequest.class);

        Map<String, Object> payload = request.toPayload();

        assertThat(validator.validate(request)).isEmpty();
        assertThat(payload)
                .containsEntry("categoria_id", 2)
                .containsEntry("nombre", "Procesador de prueba")
                .containsEntry("preciounitario", new BigDecimal("100.50"))
                .containsEntry("nucleos", 8)
                .containsEntry("socket", "AM5");
    }
}
