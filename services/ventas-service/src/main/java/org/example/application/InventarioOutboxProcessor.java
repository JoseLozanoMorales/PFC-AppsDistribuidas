package org.example.application;

import org.example.domain.FacturaDetalle;
import org.example.domain.FacturaStore;
import org.example.infrastructure.client.InventarioClient;
import org.example.infrastructure.persistence.FacturaOutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventarioOutboxProcessor {

    private static final int MAX_INTENTOS = 5;
    private static final int LOTE = 20;

    private final FacturaOutboxRepository outboxRepository;
    private final FacturaStore facturaRepository;
    private final InventarioClient inventarioClient;

    public InventarioOutboxProcessor(FacturaOutboxRepository outboxRepository,
                                     FacturaStore facturaRepository,
                                     InventarioClient inventarioClient) {
        this.outboxRepository = outboxRepository;
        this.facturaRepository = facturaRepository;
        this.inventarioClient = inventarioClient;
    }

    @Scheduled(fixedDelayString = "${outbox.inventario.retry-delay-ms:15000}")
    public void reintentarPendientes() {
        List<Integer> pendientes = outboxRepository.facturasPendientes(MAX_INTENTOS, LOTE);

        for (Integer facturaId : pendientes) {
            try {
                List<FacturaDetalle> detalle = facturaRepository.listarDetalle(facturaId);
                inventarioClient.registrarSalidasPorFactura(facturaId, detalle, "outbox-retry");
                outboxRepository.marcarProcesado(facturaId);
            } catch (Exception e) {
                outboxRepository.registrarFallo(facturaId, e.getMessage(), MAX_INTENTOS);
            }
        }
    }
}
