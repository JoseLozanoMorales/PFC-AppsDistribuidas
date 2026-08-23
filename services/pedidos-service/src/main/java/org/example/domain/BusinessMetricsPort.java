package org.example.domain;

/**
 * Puerto de dominio para eventos de negocio observables (D6.1). No expone
 * Micrometer ni ningun tipo de framework: la capa de aplicacion solo declara
 * QUE paso, la adaptacion a metricas vive en infrastructure.config.
 */
public interface BusinessMetricsPort {

    void registrarCheckoutCompletado();

    /**
     * @param motivo etiqueta de baja cardinalidad (vocabulario fijo, nunca el
     *               mensaje crudo de la excepcion) para no explotar las series
     *               temporales de Prometheus con valores unicos por peticion.
     */
    void registrarCheckoutFallido(String motivo);
}
