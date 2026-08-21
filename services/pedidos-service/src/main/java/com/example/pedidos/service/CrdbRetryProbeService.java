package com.example.pedidos.service;

import com.example.pedidos.config.CrdbMetrics;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CrdbRetryProbeService {

    private final DataSource dataSource;
    private final CrdbRetryExecutor retryExecutor;
    private final CrdbMetrics metrics;

    public CrdbRetryProbeService(DataSource dataSource, CrdbRetryExecutor retryExecutor,
                                 CrdbMetrics metrics) {
        this.dataSource = dataSource;
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
            var competidor = executor.submit(() -> ejecutarCompetidor(
                    competidorListo, sondaLeida, competidorConfirmado));

            await(competidorListo, "El competidor no alcanzo el punto de lectura");
            long valorFinal = retryExecutor.execute(() -> ejecutarSonda(
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

    private void ejecutarCompetidor(CountDownLatch listo, CountDownLatch sondaLeida,
                                    CountDownLatch confirmado) {
        try (Connection connection = nuevaTransaccion()) {
            leerValor(connection);
            listo.countDown();
            await(sondaLeida, "La sonda no alcanzo el punto de lectura");
            incrementar(connection);
            connection.commit();
            confirmado.countDown();
        } catch (SQLException e) {
            throw new IllegalStateException("Fallo la transaccion competidora", e);
        }
    }

    private long ejecutarSonda(int intento, CountDownLatch sondaLeida,
                               CountDownLatch competidorConfirmado) {
        try (Connection connection = nuevaTransaccion()) {
            leerValor(connection);
            if (intento == 1) {
                sondaLeida.countDown();
                await(competidorConfirmado, "El competidor no confirmo su escritura");
            }
            incrementar(connection);
            connection.commit();
            return leerValorConfirmado();
        } catch (SQLException e) {
            throw new IllegalStateException("Fallo serializable de la sonda", e);
        }
    }

    private Connection nuevaTransaccion() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        return connection;
    }

    private long leerValor(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT valor FROM pedidos.retry_probe WHERE probe_id = 1");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private void incrementar(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pedidos.retry_probe SET valor = valor + 1 WHERE probe_id = 1")) {
            statement.executeUpdate();
        }
    }

    private long leerValorConfirmado() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return leerValor(connection);
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
