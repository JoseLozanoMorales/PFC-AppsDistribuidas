package com.example.inventario.presentation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MovimientoInventarioRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void readsExistingSnakeCaseContract() throws Exception {
        MovimientoInventarioRequest request = objectMapper.readValue("""
                {
                  "producto_id": 10,
                  "subtipo_id": 4,
                  "cantidad": 2,
                  "referencia": "FAC-20"
                }
                """, MovimientoInventarioRequest.class);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.getProductoId()).isEqualTo(10);
        assertThat(request.getSubtipoId()).isEqualTo(4);
        assertThat(request.getCantidad()).isEqualTo(2);
        assertThat(request.getReferencia()).isEqualTo("FAC-20");
    }

    @Test
    void requiresProductSubtypeAndQuantity() {
        MovimientoInventarioRequest request = new MovimientoInventarioRequest();

        assertThat(validator.validate(request))
                .extracting(error -> error.getPropertyPath().toString())
                .containsExactlyInAnyOrder("productoId", "subtipoId", "cantidad");
    }
}
