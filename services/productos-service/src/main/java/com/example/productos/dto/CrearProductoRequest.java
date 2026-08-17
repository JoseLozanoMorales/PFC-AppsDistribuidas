package com.example.productos.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class CrearProductoRequest {
    @NotBlank(message = "nombre es obligatorio")
    private String nombre;

    @NotNull(message = "categoria_id es obligatoria")
    @Positive(message = "categoria_id debe ser positiva")
    @JsonProperty("categoria_id")
    private Integer categoriaId;

    private BigDecimal preciounitario;
    private BigDecimal costo;
    private Integer stock;
    private String enlace;

    @JsonProperty("marca_id")
    private Integer marcaId;

    @JsonProperty("gama_id")
    private Integer gamaId;

    @JsonProperty("iva_id")
    private Integer ivaId;

    @JsonProperty("valor_inventario")
    private BigDecimal valorInventario;

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
        putIfNotNull(payload, "costo", costo);
        putIfNotNull(payload, "stock", stock);
        putIfNotNull(payload, "enlace", enlace);
        putIfNotNull(payload, "marca_id", marcaId);
        putIfNotNull(payload, "gama_id", gamaId);
        putIfNotNull(payload, "iva_id", ivaId);
        putIfNotNull(payload, "valor_inventario", valorInventario);
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

    public BigDecimal getCosto() {
        return costo;
    }

    public void setCosto(BigDecimal costo) {
        this.costo = costo;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
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

    public BigDecimal getValorInventario() {
        return valorInventario;
    }

    public void setValorInventario(BigDecimal valorInventario) {
        this.valorInventario = valorInventario;
    }
}
