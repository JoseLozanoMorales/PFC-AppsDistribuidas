package org.example.service;

import org.example.client.InventarioClient;
import org.example.model.FacturaDetalle;
import org.example.repository.FacturaOutboxRepository;
import org.example.repository.FacturaStore;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("crdb")
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