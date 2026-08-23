package com.example.productos.presentation.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

// Un producto nace con stock=0 y costo=0: el stock solo sube al recibir una orden de compra
// (ordenes-proveedores-service -> inventario-service) y el costo se recalcula ahi mismo como
// promedio ponderado. Por eso este DTO ya no acepta "stock" ni "costo" -- si el cliente los
// manda igual, se ignoran (ver JdbcProductoRepository.crear).
public class CrearProductoRequest {
    @NotBlank(message = "nombre es obligatorio")
    private String nombre;

    @NotNull(message = "categoria_id es obligatoria")
    @Positive(message = "categoria_id debe ser positiva")
    @JsonProperty("categoria_id")
    private Integer categoriaId;

    @NotNull(message = "preciounitario es obligatorio")
    @PositiveOrZero(message = "preciounitario debe ser mayor o igual a 0")
    private BigDecimal preciounitario;

    private String enlace;

    @JsonProperty("marca_id")
    private Integer marcaId;

    @JsonProperty("gama_id")
    private Integer gamaId;

    @JsonProperty("iva_id")
    private Integer ivaId;

    private final Map<String, Object> atributos = new LinkedHashMap<>();

    @JsonAnySetter
    public void agregarAtributo(String nombre, Object valor) {
        atributos.put(nombre, valor);
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>(atributos);
        putIfNotNull(payload, "nombre", nombre);
        putIfNotNull(payload, "categoria_id", categoriaId);
        putIfNotNull(payload, "preciounitario", preciounitario);
        putIfNotNull(payload, "enlace", enlace);
        putIfNotNull(payload, "marca_id", marcaId);
        putIfNotNull(payload, "gama_id", gamaId);
        putIfNotNull(payload, "iva_id", ivaId);
        return payload;
    }

    private static void putIfNotNull(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Integer getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(Integer categoriaId) {
        this.categoriaId = categoriaId;
    }

    public BigDecimal getPreciounitario() {
        return preciounitario;
    }

    public void setPreciounitario(BigDecimal preciounitario) {
        this.preciounitario = preciounitario;
    }

    public String getEnlace() {
        return enlace;
    }

    public void setEnlace(String enlace) {
        this.enlace = enlace;
    }

    public Integer getMarcaId() {
        return marcaId;
    }

    public void setMarcaId(Integer marcaId) {
        this.marcaId = marcaId;
    }

    public Integer getGamaId() {
        return gamaId;
    }

    public void setGamaId(Integer gamaId) {
        this.gamaId = gamaId;
    }

    public Integer getIvaId() {
        return ivaId;
    }

    public void setIvaId(Integer ivaId) {
        this.ivaId = ivaId;
    }
}
