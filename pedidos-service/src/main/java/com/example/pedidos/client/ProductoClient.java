package com.example.pedidos.client;

import com.example.pedidos.client.dto.ProductoPrecioIva;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ProductoClient {

    private final RestClient restClient;

    public ProductoClient(RestClient.Builder restClientBuilder,
                           @Value("${productos.service.base-url}") String productosBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(productosBaseUrl).build();
    }

    /**
     * Combina GET /api/productos (precio + iva_id) con GET /api/sp/ivas (iva_id -> porcentaje)
     * para devolver precio y porcentaje de IVA de un producto.
     */
    public ProductoPrecioIva obtenerPrecioEIva(Integer productoId) {
        List<ProductoListItem> productos = restClient.get()
                .uri("/api/productos?page=0&size=1000")
                .retrieve()
                .body(new ParameterizedTypeReference<List<ProductoListItem>>() {
                });

        ProductoListItem producto = (productos == null ? List.<ProductoListItem>of() : productos).stream()
                .filter(p -> productoId.equals(p.productoId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Producto " + productoId + " no encontrado en productos-service"));

        List<IvaListItem> ivas = restClient.get()
                .uri("/api/sp/ivas")
                .retrieve()
                .body(new ParameterizedTypeReference<List<IvaListItem>>() {
                });

        BigDecimal porcentajeIva = (ivas == null ? List.<IvaListItem>of() : ivas).stream()
                .filter(i -> producto.ivaId().equals(i.ivaId()))
                .map(IvaListItem::porcentaje)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "IVA " + producto.ivaId() + " no encontrado en productos-service"));

        return new ProductoPrecioIva(producto.productoId(), producto.precioUnitario(), producto.ivaId(), porcentajeIva);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductoListItem(
            @JsonProperty("producto_id") Integer productoId,
            @JsonProperty("preciounitario") BigDecimal precioUnitario,
            @JsonProperty("iva_id") Integer ivaId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IvaListItem(
            @JsonProperty("iva_id") Integer ivaId,
            @JsonProperty("porcentaje") BigDecimal porcentaje) {
    }
}
