package com.tiendatech.pedidos.application;

import com.tiendatech.pedidos.domain.CrdbMetricsPort;
import com.tiendatech.pedidos.domain.CrdbProbePort;
import com.tiendatech.pedidos.domain.CrdbRetryPort;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CrdbRetryProbeService {

    private final CrdbProbePort probe;
    private final CrdbRetryPort retryExecutor;
    private final CrdbMetricsPort metrics;

    public CrdbRetryProbeService(CrdbProbePort probe, CrdbRetryPort retryExecutor,
                                 CrdbMetricsPort metrics) {
        this.probe = probe;
        this.retryExecutor = retryExecutor;
        this.metrics = metrics;
    }

    public Map<String, Object> provocarColision() {
        double retriesAntes = metrics.retryCount();
        AtomicInteger intentos = new AtomicInteger();
        CountDownLatch competidorListo = new CountDownLatch(1);
        CountDownLatch sondaLeida = new CountDownLatch(1);
        CountDownLatch competidorConfirmado = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            var competidor = executor.submit(() -> probe.ejecutarCompetidor(
                    competidorListo, sondaLeida, competidorConfirmado));

            await(competidorListo, "El competidor no alcanzo el punto de lectura");
            long valorFinal = retryExecutor.execute(() -> probe.ejecutarSonda(
                    intentos.incrementAndGet(), sondaLeida, competidorConfirmado));
            competidor.get(10, TimeUnit.SECONDS);

            Map<String, Object> resultado = new LinkedHashMap<>();
            resultado.put("sqlStateProvocado", "40001");
            resultado.put("intentos", intentos.get());
            resultado.put("reintentosAntes", retriesAntes);
            resultado.put("reintentosDespues", metrics.retryCount());
            resultado.put("valorFinal", valorFinal);
            return resultado;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo completar la colision controlada", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch, String mensaje) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(mensaje);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Prueba de reintento interrumpida", e);
        }
    }
}
