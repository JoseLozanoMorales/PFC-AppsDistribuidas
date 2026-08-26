package com.tiendatech.pedidos.domain;

import java.util.function.Supplier;

public interface CrdbRetryPort {
    <T> T execute(Supplier<T> operation);
}
