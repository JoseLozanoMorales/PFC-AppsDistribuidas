# ADR-009: Arquitectura basada en microservicios

- Estado: aceptada en la Entrega 1 (27 de mayo de 2026); formalizada como ADR independiente y ratificada con evidencia real en la Entrega 4 (agosto de 2026). El equipo no tenía en E1 la práctica de escribir ADR como archivo separado — esta decisión existía únicamente como prosa dentro del documento de E1 (`docs/entrega1/entrega1.pdf`, ADR-01). Este archivo la recupera y la actualiza sin alterar la decisión original.
- Fecha de origen: 2026-05-27
- Fecha de esta formalización: 2026-08-31
- Participación: decisión revisable conjuntamente por los cuatro integrantes

## Contexto

TiendaTech nace para resolver una brecha de requisitos formales en el comercio electrónico de hardware ecuatoriano: la validación de compatibilidad entre componentes, el cálculo de impuestos en tiempo real, el procesamiento de pagos y la generación de facturación electrónica exigen aislamiento de procesos para garantizar alta disponibilidad y tolerancia a fallos, en vez de acoplarse dentro de un catálogo monolítico.

## Decisión

Se adopta una arquitectura basada en microservicios, donde los módulos principales del sistema funcionan como servicios independientes: autenticación, catálogo, carrito, pagos, facturación, motor de compatibilidad y asistente inteligente.

## Alternativas consideradas

1. Arquitectura monolítica: descartada por el alto acoplamiento entre módulos, que impediría escalar de forma independiente el motor de compatibilidad (con carga computacional variable) frente al catálogo (con carga de lectura constante).
2. Arquitectura en capas N-tier: descartada por sus limitaciones para escalar servicios de forma independiente — sigue siendo un único proceso desplegable.

## Consecuencias (previstas en E1)

- Mejora la escalabilidad, disponibilidad y mantenibilidad del sistema.
- Incrementa la complejidad de comunicación entre servicios.

## Consecuencias observadas en la implementación (actualización E4)

La decisión se sostiene, pero con matices que E1 no podía anticipar sin el sistema construido:

- La separación en seis microservicios Java más `armado-ia` (Python) sí permitió escalar y depurar el motor de compatibilidad de forma aislada, tal como se previó — es, en la práctica, el servicio con el ciclo de iteración más independiente del resto (Sección 4.3, Patrones GoF aplicados).
- El costo de comunicación entre servicios resultó mayor de lo que E1 dejaba entrever como advertencia general: se manifestó en problemas concretos y verificables, no solo en "más llamadas de red" — por ejemplo, la migración de la conexión a base de datos hacia mTLS generó un conflicto de fusión real en `docker-compose.yml` porque dos mejoras (healthchecks y mTLS) se desarrollaron en paralelo sobre los mismos bloques de servicio (Sección 4.4), y el particionamiento de `pedidos.orden` obligó a propagar `fecha` como parte de la identidad de una orden en todos los servicios que la referencian (ADR-003).
- Un costo que E1 no anticipó en absoluto: mantener siete `Dockerfile` y un `docker-compose.yml` de más de 12 KB introduce una superficie de configuración considerable — el propio pipeline de CI/CD tuvo fallos derivados exclusivamente de configuración de build (contexto de Docker incorrecto para los servicios que dependen de `contracts/` fuera de su propio directorio), no de la lógica de negocio (Sección 7.3).
- La justificación original de aislar el motor de compatibilidad para "reducir el impacto de fallos" (evitar que un problema en el catálogo afecte los pagos) nunca se sometió a una prueba real hasta esta entrega: el inyector de fallos de la pasarela de pago (Paso 7) es precisamente el instrumento que falta para medir esa afirmación en vez de solo argumentarla.
