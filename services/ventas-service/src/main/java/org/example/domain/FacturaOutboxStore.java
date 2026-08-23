package org.example.domain;

import java.util.List;

/** Puerto de persistencia de eventos pendientes de inventario. */
public interface FacturaOutboxStore {
    List<Integer> facturasPendientes(int maxIntentos, int limite);
    void marcarProcesado(Integer facturaId);
    void registrarFallo(Integer facturaId, String error, int maxIntentos);
}
