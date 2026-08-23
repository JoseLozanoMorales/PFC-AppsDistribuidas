package org.example.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.cockroachdb.CockroachContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class CockroachDbIntegrationTest {

    @Container
    static final CockroachContainer cockroach =
            new CockroachContainer("cockroachdb/cockroach:v23.2.4")
                    .withDatabaseName("tiendatech_test");

    @Test
    void ejecutaTransaccionSerializableConDriverPostgresql() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                cockroach.getJdbcUrl(),
                cockroach.getUsername(),
                cockroach.getPassword())) {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);

            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE pedido_prueba (
                            fecha DATE NOT NULL,
                            orden_id INT8 NOT NULL,
                            total DECIMAL(18,2) NOT NULL,
                            PRIMARY KEY (fecha, orden_id)
                        )
                        """);
                statement.executeUpdate("""
                        INSERT INTO pedido_prueba (fecha, orden_id, total)
                        VALUES (DATE '2026-07-28', 1, 125.50)
                        """);
            }
            connection.commit();

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT total FROM pedido_prueba WHERE orden_id = 1")) {
                result.next();
                assertEquals("125.50", result.getBigDecimal("total").toPlainString());
            }
        }
    }
}
