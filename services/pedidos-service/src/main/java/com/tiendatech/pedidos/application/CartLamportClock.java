package com.tiendatech.pedidos.application;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CartLamportClock {
    private final ConcurrentHashMap<Long, AtomicLong> clocks = new ConcurrentHashMap<>();

    public long receive(long cartId, long remote) {
        return clocks.computeIfAbsent(cartId, ignored -> new AtomicLong())
                .updateAndGet(local -> Math.max(local, remote) + 1);
    }

    public long receiveResponse(long cartId, long remote) { return receive(cartId, remote); }
}
