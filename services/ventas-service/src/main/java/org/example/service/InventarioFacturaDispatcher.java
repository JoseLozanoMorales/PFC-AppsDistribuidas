package org.example.service;

import org.example.client.InventarioClient;
import org.example.model.FacturaDetalle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mantiene el comportamiento del stack PostgreSQL mientras el outbox se usa
 * en el perfil CRDB. En CRDB este bean no existe: la transacción crea el evento
 * y {@link InventarioOutboxProcessor} lo entrega de forma diferida.
 */
@Component
@Profile("!crdb")
public class InventarioFacturaDispatcher {

    private final InventarioClient inventarioClient;

    public InventarioFacturaDispatcher(InventarioClient inventarioClient) {
        this.inventarioClient = inventarioClient;
    }

    public void despachar(Integer facturaId, List<FacturaDetalle> detalle) {
        try {
            inventarioClient.registrarSalidasPorFactura(facturaId, detalle, null);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "La factura " + facturaId
                            + " se genero, pero fallo el descuento de stock en inventario-service",
                    error);
        }
    }
}
