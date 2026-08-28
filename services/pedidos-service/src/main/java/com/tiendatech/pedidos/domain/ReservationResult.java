package com.tiendatech.pedidos.domain;

public record ReservationResult(boolean accepted, String message, int reservedQuantity,
                                int availableStock, long lamportTimestamp,
                                String winningDeviceId, boolean replayed) {
}
