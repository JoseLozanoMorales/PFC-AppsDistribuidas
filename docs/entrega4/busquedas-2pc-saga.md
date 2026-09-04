# Búsquedas para el estado del arte: 2PC y Saga

Estas sentencias están preparadas para buscar literatura; no son resultados de una búsqueda ni referencias verificadas. Prueba primero las expresiones breves y amplía después con los términos indicados.

Actualización del 3 de septiembre de 2026: se incorporaron cinco referencias en `estado-arte-2pc-saga.tex`. Sus metadatos y el alcance de consulta se registran en [bibliografia-2pc-saga.json](cierre/bibliografia-2pc-saga.json). Las sentencias siguientes se conservan para ampliar o repetir la búsqueda.

| Propósito | Sentencia de búsqueda |
|---|---|
| Comparación directa | `"two-phase commit" "saga"` |
| Comparación en microservicios | `"two-phase commit" "saga" microservices` |
| Latencia y rendimiento | `"two-phase commit" "saga" performance latency` |
| Consistencia y aislamiento | `"saga" "transactions" isolation anomalies` |
| Compensación y recuperación | `"saga" "compensating transactions" "recovery"` |
| Bloqueo y fallo del coordinador | `"two-phase commit" "blocking" "coordinator failure"` |
| Evaluación experimental | `"saga" "two-phase commit" benchmark` |
| Reintentos y duplicación | `"saga" "idempotency" "distributed transactions"` |

Si una consulta devuelve pocos resultados, elimina primero el último término. Para la comparación directa, prueba también `2PC` en lugar de `"two-phase commit"` y `sagas` en lugar de `saga`. Evita usar “saga” sola porque recupera literatura ajena a transacciones.

## Cadena ampliada

Para buscadores académicos que admitan operadores booleanos y agrupación:

```text
("two-phase commit" OR "two phase commit" OR "2PC")
AND ("saga" OR "sagas" OR "compensating transactions")
AND ("distributed transactions" OR "microservices")
```

Para acotar al experimento del proyecto, añade un bloque cada vez:

```text
AND (performance OR latency OR throughput OR benchmark)
```

```text
AND (consistency OR isolation OR recovery OR "fault injection")
```

## Qué seleccionar y registrar

Conviene que las nuevas fuentes cubran: fundamentos de Saga, costes y bloqueo de 2PC, comparación experimental, aislamiento/anomalías y recuperación/compensación. Una misma fuente puede cubrir varios aspectos. No fuerces cinco referencias si no aportan contenido distinto.

Por cada candidata registra título, autores, año, DOI o identificador, enlace editorial y páginas/secciones leídas. Anota la afirmación concreta que respalda, su topología experimental, fallos considerados, nivel de concurrencia y límites de aplicación a TiendaTech. Prioriza trabajos originales para resultados y protocolos; usa revisiones para contextualizar y encontrar los originales. No presentes una propuesta teórica o un ejemplo local como prueba sobre microservicios reales.

Antes de citar, verifica que el DOI corresponda al título y autores y que el texto leído sostenga la afirmación. Mantén los clásicos pertinentes y complementa con comparaciones experimentales recientes, sin aplicar un filtro de fecha que elimine los fundamentos.
