# Bitácora de tolerancia a fallos del cluster CockroachDB

- Fecha: 2026-07-28
- Dataset: 500 000 órdenes, 500 000 detalles y 10 000 usuarios
- Cluster: tres nodos CockroachDB 23.2.4
- Factor de replicación: 3
- Quórum requerido: 2
- Script: `docs/evidencias/probar-tolerancia-fallos.ps1`
- Datos crudos: `docs/evidencias/resultados-tolerancia/mediciones.csv`

## Consulta utilizada

```sql
SELECT count(*) AS ordenes_dia
FROM pedidos.orden
WHERE fecha = DATE '2026-07-15';
```

El resultado esperado con quórum fue de 1 370 órdenes. Se estableció un
`statement_timeout` de ocho segundos para registrar de forma acotada la pérdida
de disponibilidad.

## Procedimiento

1. Confirmar los tres nodos en ejecución.
2. Ejecutar cinco repeticiones con los tres nodos disponibles.
3. Detener abruptamente `crdb-2` con `docker kill`.
4. Ejecutar cinco repeticiones conservando `crdb-1` y `crdb-3`.
5. Reincorporar `crdb-2` y ejecutar cinco repeticiones.
6. Detener simultáneamente `crdb-2` y `crdb-3`.
7. Ejecutar una consulta con un único nodo y timeout de ocho segundos.
8. Restaurar los nodos y comprobar el estado final.

## Resultados

| Etapa | Intentos | Éxitos | Promedio (ms) | Máximo (ms) |
|---|---:|---:|---:|---:|
| Tres nodos | 5 | 5 | 428,33 | 595,09 |
| Un nodo caído | 5 | 5 | 423,44 | 631,52 |
| Nodo reincorporado | 5 | 5 | 386,02 | 486,56 |
| Dos nodos caídos | 1 | 0 | — | — |

La consulta con dos nodos caídos terminó después de 8 890,15 ms con:

```text
ERROR: query execution canceled due to statement timeout
SQLSTATE: 57014
```

## Interpretación

Con la caída de un nodo permanecieron dos réplicas votantes y, por tanto, el
quórum mayoritario. Las cinco consultas fueron exitosas y devolvieron el mismo
conteo de 1 370 órdenes.

Con dos nodos caídos quedó una sola réplica votante. El cluster no pudo alcanzar
quórum y no devolvió una lectura potencialmente inconsistente; la consulta
finalizó por timeout. Este comportamiento coincide con un sistema que prioriza
consistencia ante la pérdida de mayoría.

Tras restaurar los nodos, `cockroach node status` confirmó nuevamente:

```text
crdb-1  is_available=true  is_live=true
crdb-2  is_available=true  is_live=true
crdb-3  is_available=true  is_live=true
```

## Limitaciones y evidencia pendiente

- Estas mediciones corresponden a una consulta de disponibilidad, no a un
  benchmark concurrente.
- Se realizaron cinco repeticiones por estado; el protocolo del pipeline
  paralelo utilizará diez repeticiones por configuración.
- Falta grabar el vídeo exigido por la guía. El vídeo debe reproducir este mismo
  script o mostrar los estados guardados y las consultas en vivo.
