package org.example.domain;

import java.util.function.Supplier;

public interface CrdbRetryPort {
    <T> T execute(Supplier<T> operation);
}
