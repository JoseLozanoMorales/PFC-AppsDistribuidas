package org.example.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class CrdbMetrics {

    private final MeterRegistry registry;
    private final Counter transactionRetries;
    private final Timer queryDuration;

    public CrdbMetrics(MeterRegistry registry, DataSource dataSource) {
        this.registry = registry;
        this.transactionRetries = Counter.builder("crdb.transaction.retries")
                .description("Reintentos de transacciones serializables por SQLSTATE 40001")
                .register(registry);
        this.queryDuration = Timer.builder("crdb.query.duration")
                .description("Duración de operaciones transaccionales contra CockroachDB")
                .publishPercentileHistogram()
                .register(registry);

        if (dataSource instanceof HikariDataSource hikari) {
            Gauge.builder("crdb.pool.active.connections", hikari, CrdbMetrics::activeConnections)
                    .description("Conexiones activas del pool JDBC hacia CockroachDB")
                    .register(registry);
        }
    }

    private static int activeConnections(HikariDataSource dataSource) {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        return pool == null ? 0 : pool.getActiveConnections();
    }

    public Timer.Sample startQuery() {
        return Timer.start(registry);
    }

    public void stopQuery(Timer.Sample sample) {
        sample.stop(queryDuration);
    }

    public void recordRetry() {
        transactionRetries.increment();
    }

    public double retryCount() {
        return transactionRetries.count();
    }
}
