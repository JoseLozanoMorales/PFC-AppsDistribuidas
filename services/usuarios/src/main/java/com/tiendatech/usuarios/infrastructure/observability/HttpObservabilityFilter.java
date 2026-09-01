package com.tiendatech.usuarios.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class HttpObservabilityFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(HttpObservabilityFilter.class);

    private final MeterRegistry registry;
    private final AtomicInteger active = new AtomicInteger();
    private final String service;

    public HttpObservabilityFilter(MeterRegistry registry, @Value("${spring.application.name}") String service) {
        this.registry = registry;
        this.service = service;
        Gauge.builder("active_connections", active, AtomicInteger::get).tag("service", service).register(registry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        active.incrementAndGet();
        try {
            chain.doFilter(req, res);
        } finally {
            registrar(req, res, System.nanoTime() - start);
            active.decrementAndGet();
        }
    }

    private void registrar(HttpServletRequest req, HttpServletResponse res, long elapsedNanos) {
        RequestLabels labels = labels(req, res);
        registrarMetricas(labels, elapsedNanos);
        registrarLog(labels, elapsedNanos);
    }

    private RequestLabels labels(HttpServletRequest req, HttpServletResponse res) {
        return new RequestLabels(req.getMethod(), req.getRequestURI(), Integer.toString(res.getStatus()));
    }

    private void registrarMetricas(RequestLabels labels, long elapsedNanos) {
        Counter.builder("request_count").tags(labels.tags(service)).register(registry).increment();
        Timer.builder("request_duration").publishPercentileHistogram()
                .tags(labels.tags(service)).register(registry).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    private void registrarLog(RequestLabels labels, long elapsedNanos) {
        MDC.put("service", service);
        MDC.put("method", labels.method());
        MDC.put("route", labels.route());
        MDC.put("status", labels.status());
        MDC.put("response_time_ms", Long.toString(TimeUnit.NANOSECONDS.toMillis(elapsedNanos)));
        LOG.info("http_request_completed");
        MDC.clear();
    }

    private record RequestLabels(String method, String route, String status) {
        String[] tags(String service) {
            return new String[] {"service", service, "method", method, "route", route, "status", status};
        }
    }
}