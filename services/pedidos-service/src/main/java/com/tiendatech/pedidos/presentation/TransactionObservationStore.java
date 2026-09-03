package com.tiendatech.pedidos.presentation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Historial acotado en memoria para evidencia operacional; no forma parte del dominio. */
@Component
public class TransactionObservationStore {
    private static final int MAX_ENTRIES = 200;
    private final ConcurrentLinkedDeque<TransactionObservation> entries = new ConcurrentLinkedDeque<>();
    private final String coordination;

    public TransactionObservationStore(@Value("${COORD:2pc}") String coordination) {
        this.coordination = "saga".equalsIgnoreCase(coordination == null ? "" : coordination.trim()) ? "saga" : "2pc";
    }

    public void add(Integer orderId, String traceId, String state, long latencyMs, String failure, Instant timestamp) {
        entries.addFirst(new TransactionObservation(orderId, traceId, coordination, state, latencyMs, failure, timestamp));
        while (entries.size() > MAX_ENTRIES) entries.pollLast();
    }

    public List<TransactionObservation> recent() {
        return new ArrayList<>(entries);
    }

    public record TransactionObservation(Integer orderId, String traceId, String strategy, String finalState,
                                         long latencyMs, String failure, Instant timestamp) {}
}
