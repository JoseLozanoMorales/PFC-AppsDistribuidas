package com.tiendatech.ventas.application;

import com.tiendatech.ventas.domain.FacturaDetalle;
import com.tiendatech.ventas.domain.FacturaStore;
import com.tiendatech.ventas.domain.FacturaOutboxStore;
import com.tiendatech.ventas.domain.InventarioPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventarioOutboxProcessor {

    private static final int MAX_INTENTOS = 5;
    private static final int LOTE = 20;

    private final FacturaOutboxStore outboxRepository;
    private final FacturaStore facturaRepository;
    private final InventarioPort inventarioClient;

    public InventarioOutboxProcessor(FacturaOutboxStore outboxRepository,
                                     FacturaStore facturaRepository,
                                     InventarioPort inventarioClient) {
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
