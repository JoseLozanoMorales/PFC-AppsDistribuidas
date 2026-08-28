package com.tiendatech.pedidos.domain;

public interface ReservationPort {
    ReservationResult reconcile(ReservationCommand command);
}
