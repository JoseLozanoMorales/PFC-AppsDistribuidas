package com.example.productos.config;

import com.example.productos.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsMissingProductToNotFound() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleNotFound(new ResourceNotFoundException("Producto no encontrado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Producto no encontrado");
    }

    @Test
    void mapsInvalidInputToBadRequest() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Dato inválido"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Dato inválido");
    }

    @Test
    void doesNotExposeUnexpectedExceptionDetails() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleUnexpected(new RuntimeException("detalle interno sensible"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsOnly(Map.entry("error", "Unexpected error"));
    }

    @Test
    void mapsValidationErrorsToBadRequest() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "nombre", "nombre es obligatorio"));
        MethodParameter parameter = new MethodParameter(
                ApiExceptionHandlerTest.class.getDeclaredMethod("dummy", Object.class), 0);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(parameter, binding);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Validation error");
        assertThat(response.getBody()).containsEntry("fields", Map.of("nombre", "nombre es obligatorio"));
    }

    @Test
    void mapsMalformedJsonToBadRequest() {
        ResponseEntity<Map<String, Object>> response = handler.handleUnreadableMessage(
                new HttpMessageNotReadableException("Malformed JSON"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsOnly(Map.entry("error", "Malformed JSON request"));
    }

    private void dummy(Object request) {
    }
}
