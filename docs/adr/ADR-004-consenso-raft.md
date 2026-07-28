# ADR-004: Replicación y consenso Raft en CockroachDB

- Estado: aceptada para la Entrega 3
- Fecha: 2026-07-28
- Participación: decisión revisable conjuntamente por los cuatro integrantes

## Contexto

La Entrega 3 exige reemplazar la persistencia mono-nodo por un cluster
CockroachDB de tres nodos y demostrar consistencia serializable y tolerancia a
fallos. Cada rango de CockroachDB utiliza un grupo Raft para replicar sus
escrituras.

## Decisión

Se desplegarán tres nodos CockroachDB con factor de replicación `f = 3`. Una
escritura se considerará confirmada cuando alcance el quórum mayoritario:

`quorum = floor(f / 2) + 1 = 2`.

El microservicio de Pedidos ejecutará las operaciones críticas con aislamiento
`SERIALIZABLE` y reintentará únicamente los errores de serialización
identificados por SQLSTATE `40001`.

## Comportamiento esperado

- Con tres nodos: lecturas y escrituras disponibles.
- Con un nodo detenido: permanecen dos réplicas; existe quórum y el sistema debe
  continuar disponible.
- Con dos nodos detenidos: queda una réplica; no existe quórum y se espera
  pérdida de disponibilidad sin aceptar escrituras inconsistentes.
- Tras reincorporar el nodo: los rangos deben recuperar su estado disponible.

## Alternativas consideradas

1. PostgreSQL mono-nodo: mantiene compatibilidad, pero no permite demostrar
   consenso ni tolerancia a fallos.
2. Réplica primaria-secundaria manual: añade disponibilidad, pero no proporciona
   la misma semántica de consenso por rango.
3. Cinco nodos: toleraría dos fallos, aunque incrementaría recursos y excedería
   el alcance exigido.
4. Factor de replicación 1: reduce almacenamiento, pero elimina la tolerancia
   solicitada.

## Consecuencias

- Una caída mantiene disponibilidad solo mientras exista mayoría.
- La latencia de escritura incorpora el costo del consenso.
- Las transacciones pueden recibir errores de serialización y deben reintentarse
  de forma acotada.
- La prueba experimental debe registrar latencia antes, durante y después del
  fallo, además de la indisponibilidad esperada con dos nodos detenidos.

## Evidencia requerida

- `cockroach node status --insecure` con tres nodos disponibles.
- `SHOW ZONE CONFIGURATION FOR RANGE default`.
- Bitácora y vídeo de la caída de uno y dos nodos.
- Latencia observada en las tres etapas.
- Métrica `crdb_transaction_retries_total`.
