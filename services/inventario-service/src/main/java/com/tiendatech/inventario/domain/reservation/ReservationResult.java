package com.tiendatech.inventario.domain.reservation;

public record ReservationResult(boolean accepted, String message, int reservedQuantity,
                                int availableStock, long lamportTimestamp,
                                String winningDeviceId, boolean replayed) {
    public ReservationResult asReplay() {
        return new ReservationResult(accepted, message, reservedQuantity, availableStock,
                lamportTimestamp, winningDeviceId, true);
    }
}
