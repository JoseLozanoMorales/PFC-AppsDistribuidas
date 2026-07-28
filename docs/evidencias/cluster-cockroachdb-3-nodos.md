# Evidencia del cluster CockroachDB de tres nodos

- Fecha: 2026-07-28
- Versión: CockroachDB 23.2.4
- Perfil Compose: `e3-crdb`
- Esquema aplicado: `docs/db/schema.sql`

## Arranque reproducible

```powershell
docker compose --profile e3-crdb up -d crdb-1 crdb-2 crdb-3 crdb-init
```

El perfil es deliberado: permite trabajar en la migración de E3 sin reemplazar
todavía la conexión PostgreSQL utilizada por los servicios de E2.

## Estado de nodos

La orden:

```powershell
docker exec tiendatech-crdb-1 cockroach node status `
  --insecure --host=localhost:26257
```

devolvió:

| Nodo | Dirección | Versión | Localidad | Disponible | Vivo |
|---:|---|---|---|---|---|
| 1 | `crdb-1:26257` | v23.2.4 | `region=local,zone=z1` | true | true |
| 2 | `crdb-2:26257` | v23.2.4 | `region=local,zone=z2` | true | true |
| 3 | `crdb-3:26257` | v23.2.4 | `region=local,zone=z3` | true | true |

## Rangos y replicación

`SHOW RANGES FROM TABLE pedidos.orden WITH DETAILS` confirmó seis intervalos
físicos separados por los cinco límites trimestrales. En todos los intervalos
se observaron:

```text
replicas        = {1,2,3}
voting_replicas = {1,2,3}
```

Las concesiones de los rangos quedaron repartidas entre las zonas `z1`, `z2` y
`z3`. La configuración predeterminada confirmó:

```text
num_replicas = 3
```

## Puertos del entorno local

| Nodo | SQL | Consola web |
|---|---:|---:|
| crdb-1 | 26257 | 8091 |
| crdb-2 | 26258 | 8092 |
| crdb-3 | 26259 | 8093 |

## Interpretación

Esta evidencia satisface el estado operativo inicial de D2.1: tres nodos vivos,
esquema aplicado y rangos con tres réplicas votantes. Todavía no satisface D2.2,
porque falta ejecutar y registrar la caída controlada de uno y dos nodos con
mediciones de latencia antes, durante y después del fallo.
