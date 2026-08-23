package com.example.inventario.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class InventarioSchemaInitializer {
    private final JdbcTemplate jdbc;

    public InventarioSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initializeIdempotencyLedger() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS inventario.solicitud_idempotente (
                    clave VARCHAR(200) PRIMARY KEY,
                    payload_hash VARCHAR(64) NOT NULL,
                    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);
    }
}
