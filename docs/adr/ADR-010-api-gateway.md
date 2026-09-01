# ADR-010: Uso de API Gateway

- Estado: aceptada en la Entrega 1 (27 de mayo de 2026); formalizada como ADR independiente y ratificada con evidencia real en la Entrega 4 (agosto de 2026). Existía únicamente como prosa dentro de `docs/entrega1/entrega1.pdf` (ADR-02).
- Fecha de origen: 2026-05-27
- Fecha de esta formalización: 2026-08-31
- Participación: decisión revisable conjuntamente por los cuatro integrantes

## Contexto

Con varios microservicios independientes, el cliente web y móvil necesitan un único punto de entrada para las solicitudes, en vez de conocer la dirección de cada microservicio por separado.

## Decisión

Se implementa un API Gateway (Spring Cloud Gateway) como punto de entrada único para las solicitudes del cliente web y móvil.

## Alternativas consideradas

1. Comunicación directa entre cliente y microservicios: descartada por riesgos de seguridad (cada microservicio expuesto directamente al host) y mayor acoplamiento del cliente con la topología interna del backend.
2. Backend for Frontend (BFF): descartado porque en la fase de E1 solo se contemplaba un cliente web principal; no se justificaba la complejidad de un BFF por cliente.

## Consecuencias (previstas en E1)

- Facilita el control de solicitudes y la organización de la arquitectura.
- Se convierte en un componente crítico que debe manejarse con tolerancia a fallos.

## Consecuencias observadas en la implementación (actualización E4)

- La decisión resultó más importante de lo que E1 anticipaba: el Gateway terminó siendo también el punto único de autenticación centralizada. ADR-007 (`docs/adr/ADR-007-patron-jwt-gateway.md`) formaliza esta extensión natural — el Gateway valida el JWT, y los microservicios internos ni siquiera publican su puerto al host, confiando en el aislamiento de la red Docker y en las cabeceras `X-User-*` que el propio Gateway sobrescribe. E1 no había anticipado que el Gateway asumiría también el rol de frontera de seguridad, no solo de enrutamiento.
- La advertencia de E1 sobre "componente crítico que debe manejarse con tolerancia a fallos" no se ha verificado todavía con evidencia experimental: `GatewayIntegrationTest` prueba que el Gateway enruta correctamente y rechaza peticiones sin token (Sección 7.1), pero no prueba qué ocurre si el Gateway mismo cae o se satura bajo carga. Esto queda como una limitación declarada, no resuelta, y coincide con el alcance del experimento del Paso 8.
- Un aspecto que en la práctica se agregó sin haber sido parte de la decisión original: Caddy como reverse proxy con TLS delante del propio Gateway, que en E1 no se contemplaba porque el sistema todavía no exponía tráfico HTTPS real.
