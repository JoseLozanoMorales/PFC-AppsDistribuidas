package com.tiendatech.pedidos.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CartLamportClockTest {

    @Test
    void avanzaElRelojPorCarritoDeFormaIndependiente() {
        var clock = new CartLamportClock();

        assertEquals(6, clock.receive(1, 5));
        assertEquals(7, clock.receive(1, 3));
        assertEquals(11, clock.receive(1, 10));

        assertEquals(4, clock.receive(2, 3));
        assertEquals(11, clock.receive(2, 10));
    }

    @Test
    void receiveResponseDelegaEnReceive() {
        var clock = new CartLamportClock();

        assertEquals(6, clock.receiveResponse(1, 5));
        assertEquals(7, clock.receiveResponse(1, 3));
    }
}
