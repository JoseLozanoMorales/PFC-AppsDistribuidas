package com.tiendatech.inventario.application.reservation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LamportClockTest {

    @Test
    void avanzaElRelojAlMaximoEntreLocalYRemotoMasUno() {
        var clock = new LamportClock();
        assertEquals(0, clock.current());

        assertEquals(6, clock.receive(5));
        assertEquals(6, clock.current());

        assertEquals(7, clock.receive(3));
        assertEquals(7, clock.current());

        assertEquals(20, clock.receive(19));
        assertEquals(20, clock.current());
    }
}
