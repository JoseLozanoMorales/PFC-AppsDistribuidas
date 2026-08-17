package com.example.inventario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardTest {
    private JdbcTemplate jdbc;
    private IdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        guard = new IdempotencyGuard(jdbc, new ObjectMapper());
    }

    @Test
    void firstRequestIsProcessed() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        boolean replayed = guard.isReplay("FAC-10", payload(2));

        assertThat(replayed).isFalse();
    }

    @Test
    void sameKeyAndPayloadIsReplay() {
        Object payload = payload(2);
        String hash = guard.hash(payload);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("FAC-10")))
                .thenReturn(hash);

        boolean replayed = guard.isReplay("FAC-10", payload);

        assertThat(replayed).isTrue();
    }

    @Test
    void sameKeyWithDifferentPayloadIsConflict() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("FAC-10")))
                .thenReturn(guard.hash(payload(1)));

        assertThatThrownBy(() -> guard.isReplay("FAC-10", payload(2)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void missingKeyKeepsBackwardCompatibility() {
        assertThat(guard.isReplay(null, payload(2))).isFalse();
        verify(jdbc, org.mockito.Mockito.never()).update(anyString(), any(Object[].class));
    }

    private static List<Map<String, Object>> payload(int cantidad) {
        return List.of(Map.of(
                "producto_id", 1,
                "subtipo_id", 4,
                "cantidad", cantidad,
                "referencia", "FAC-10"));
    }
}
