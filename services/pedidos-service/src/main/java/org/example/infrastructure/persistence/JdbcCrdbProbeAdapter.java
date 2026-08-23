package org.example.infrastructure.persistence;

import org.example.domain.CrdbProbePort;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
public class JdbcCrdbProbeAdapter implements CrdbProbePort {
    private final DataSource dataSource;

    public JdbcCrdbProbeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void ejecutarCompetidor(CountDownLatch listo, CountDownLatch sondaLeida,
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

    @Override
    public long ejecutarSonda(int intento, CountDownLatch sondaLeida,
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
