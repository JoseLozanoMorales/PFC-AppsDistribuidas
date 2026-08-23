package org.example.infrastructure.config;

import org.example.domain.CrdbRetryPort;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.function.Supplier;

@Component
public class CrdbRetryExecutor implements CrdbRetryPort {

    private static final String SERIALIZATION_FAILURE = "40001";

    private final int maxAttempts;
    private final long delayMs;
    private final CrdbMetrics metrics;

    public CrdbRetryExecutor(
            @Value("${crdb.retry.max-attempts:3}") int maxAttempts,
            @Value("${crdb.retry.delay-ms:100}") long delayMs,
            CrdbMetrics metrics) {
        this.maxAttempts = maxAttempts;
        this.delayMs = delayMs;
        this.metrics = metrics;
    }

    @Override
    public <T> T execute(Supplier<T> operation) {
        Timer.Sample sample = metrics.startQuery();
        try {
            for (int attempt = 1; ; attempt++) {
                try {
                    return operation.get();
                } catch (RuntimeException exception) {
                    if (!hasSqlState(exception, SERIALIZATION_FAILURE) || attempt >= maxAttempts) {
                        throw exception;
                    }
                    metrics.recordRetry();
                    pause(attempt);
                }
            }
        } finally {
            metrics.stopQuery(sample);
        }
    }

    static boolean hasSqlState(Throwable error, String expectedState) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && expectedState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(delayMs * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento CockroachDB interrumpido", interrupted);
        }
    }
}
