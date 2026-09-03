package com.tiendatech.pedidos.infrastructure.observability;

/** Contexto acotado al hilo de la solicitud; siempre debe limpiarse en finally. */
public final class TraceContext {
    private static final ThreadLocal<Value> CURRENT = new ThreadLocal<>();

    private TraceContext() {}

    public static void set(String traceId, String failureMode) {
        CURRENT.set(new Value(traceId, failureMode));
    }

    public static String traceId() {
        Value value = CURRENT.get();
        return value == null ? null : value.traceId();
    }

    public static String failureMode() {
        Value value = CURRENT.get();
        return value == null ? null : value.failureMode();
    }

    public static void clear() {
        CURRENT.remove();
    }

    private record Value(String traceId, String failureMode) {}
}
