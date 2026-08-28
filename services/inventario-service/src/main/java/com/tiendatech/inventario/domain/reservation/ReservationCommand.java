package com.tiendatech.inventario.domain.reservation;

public record ReservationCommand(long cartId, long userId, long productId, int quantity,
                                 long lamportTimestamp, String deviceId, String operationId) {
}
