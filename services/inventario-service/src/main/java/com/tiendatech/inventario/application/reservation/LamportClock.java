package com.tiendatech.inventario.application.reservation;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class LamportClock {
    private final AtomicLong value = new AtomicLong();

    public long receive(long remoteTimestamp) {
        return value.updateAndGet(local -> Math.max(local, remoteTimestamp) + 1);
    }

    public long current() {
        return value.get();
    }
}
