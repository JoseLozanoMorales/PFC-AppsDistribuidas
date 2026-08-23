package com.example.inventario.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class IdempotencyGuard {
    private static final int MAX_KEY_LENGTH = 200;

    private final JdbcTemplate jdbc;
    private final ObjectMapper canonicalMapper;

    public IdempotencyGuard(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /**
     * Registra la solicitud dentro de la transaccion de inventario.
     *
     * @return true si la misma solicitud ya habia sido procesada; false si debe procesarse ahora.
     */
    public boolean isReplay(String rawKey, Object payload) {
        if (rawKey == null || rawKey.isBlank()) {
            return false;
        }

        String key = rawKey.trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key no puede superar 200 caracteres");
        }

        String payloadHash = hash(payload);
        int inserted = jdbc.update("""
                INSERT INTO inventario.solicitud_idempotente (clave, payload_hash)
                VALUES (?, ?)
                ON CONFLICT (clave) DO NOTHING
                """, key, payloadHash);

        if (inserted == 1) {
            return false;
        }

        String storedHash = jdbc.queryForObject("""
                SELECT payload_hash
                FROM inventario.solicitud_idempotente
                WHERE clave = ?
                """, String.class, key);

        if (!payloadHash.equals(storedHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key ya fue utilizada con un contenido diferente");
        }
        return true;
    }

    String hash(Object payload) {
        try {
            byte[] canonicalPayload = canonicalMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonicalPayload));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No fue posible calcular la huella idempotente", ex);
        }
    }
}
