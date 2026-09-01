package com.tiendatech.pedidos.infrastructure.client;

import com.tiendatech.pedidos.domain.FacturaPort;
import com.tiendatech.pedidos.domain.DetalleOrden;
import com.tiendatech.pedidos.domain.Orden;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.List;
import java.util.function.Supplier;

@Component
public class FacturaClient implements FacturaPort {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public FacturaClient(RestClient.Builder restClientBuilder,
                         @Value("${ventas.service.base-url}") String ventasBaseUrl,
                         CircuitBreakerRegistry circuitBreakerRegistry,
                         InboundAuthorizationInterceptor authorizationInterceptor) {
        this.restClient = restClientBuilder.baseUrl(ventasBaseUrl)
                .requestInterceptor(authorizationInterceptor)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("facturaClient");
    }

    // Escritura (crea una factura en ventas-service): solo circuit breaker, SIN
    // reintento. Este servicio no controla ventas-service y no puede demostrar
    // que POST /api/facturas sea idempotente; reintentar tras un timeout podria
    // duplicar la factura de la misma orden. El llamador (OrdenService) ya
    // convierte cualquier fallo aqui en un mensaje explicito: la orden quedo
    // creada pero la facturacion fallo, en vez de fingir exito o reintentar solo.
    @Override
    public Integer generarFactura(Orden orden, List<DetalleOrden> detalle) {
        List<Map<String, Object>> lineas = detalle.stream().map(item -> Map.<String, Object>of(
                "productoId", item.getProductoId(), "cantidad", item.getCantidad(),
                "precio", item.getPrecioUnitario(), "subtotal", item.getSubtotal(),
                "iva", item.getIva(), "total", item.getTotal())).toList();
        Map<String, Object> snapshot = Map.of(
                "ordenId", orden.getOrdenId(), "fechaOrden", orden.getFecha(),
                "usuarioId", orden.getUsuarioId(), "subtotal", orden.getSubtotal(),
                "total", orden.getTotal(), "lineas", lineas);
        Supplier<Map> llamada = () -> restClient.post()
                .uri("/api/facturas")
                .body(snapshot)
                .retrieve()
                .body(Map.class);
        Map resp = CircuitBreaker.decorateSupplier(circuitBreaker, llamada).get();
        Map data = resp != null && resp.get("data") instanceof Map envelopeData ? envelopeData : resp;
        if (data == null || !(data.get("facturaId") instanceof Number facturaId)) {
            throw new IllegalStateException("ventas-service no devolvió el identificador de la factura");
        }
        return facturaId.intValue();
    }
}
