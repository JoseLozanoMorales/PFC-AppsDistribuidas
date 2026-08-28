package com.tiendatech.pedidos.domain;

public record ReservationCommand(long cartId, long userId, long productId, int quantity,
                                 long lamportTimestamp, String deviceId, String operationId) {
}
