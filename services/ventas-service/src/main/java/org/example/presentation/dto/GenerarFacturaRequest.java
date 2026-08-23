package org.example.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class GenerarFacturaRequest {

    @NotNull(message = "ordenId es obligatorio")
    @Positive(message = "ordenId debe ser un numero positivo")
    private Integer ordenId;

    public GenerarFacturaRequest() {}

    public GenerarFacturaRequest(Integer ordenId) {
        this.ordenId = ordenId;
    }

    public Integer getOrdenId() { return ordenId; }
    public void setOrdenId(Integer ordenId) { this.ordenId = ordenId; }
}
