# Evidencia de arranque completo desde volúmenes limpios (Paso 15, ítem 3 / P3)

- Fecha: 2026-09-04
- Objetivo: repetir la comprobación del Paso 4 (clonación limpia, seguir el
  README, compilar el `.tex`, levantar el sistema) sobre el estado actual del
  repositorio, para verificar si el bloqueo de arranque reportado en pasos
  anteriores sigue vigente.
- Máquina: equipo local de Jhinson Aucatoma (Windows, Docker Desktop).
- **Estado del código probado: local, no integrado a `main`.** `docker-compose.yml`
  y `.env.example` en el momento de esta prueba incluyen cambios sin commitear
  (ver nota al final); esta evidencia demuestra que el fix funciona, no que ya
  esté disponible para el resto del equipo.

## El bloqueo reportado

CockroachDB rechaza el arranque cuando el reloj de un nodo (o del cliente que
ejecuta `schema.sql` durante la inicialización) diverge más de 500 ms del resto
del clúster — error `remote wall time is too far ahead`. Es un problema conocido
bajo Docker Desktop/WSL2 y ya se había visto antes, de forma equivalente, en el
nodo único del Paso 5.6.

## El fix

`--max-offset=5s` agregado al comando `start` de los tres nodos CockroachDB en
`docker-compose.yml`. Amplía la tolerancia de desfase de reloj aceptada; no
altera cómo se miden los tiempos en el resto de los experimentos.

## Procedimiento ejecutado

```powershell
docker compose down -v
cp .env.example .env
docker compose up -d --build
```

Los 8 servicios de imagen propia se reconstruyeron sin errores. Se crearon las
redes y los volúmenes nuevos (`crdb-1-data`, `crdb-2-data`, `crdb-3-data`,
`prometheus-data`) — es decir, el clúster CockroachDB partió de cero, sin
estado previo. `tiendatech-crdb-init` corrió el `schema.sql` y salió con
código `0`:

```powershell
docker inspect tiendatech-crdb-init --format='{{.State.ExitCode}}'
0
```

## Estado de los 13 contenedores (`docker compose ps`)

| Servicio | Estado | Salud |
|---|---|---|
| tiendatech-armado-ia | Up | healthy |
| tiendatech-crdb-1 | Up | — (sin healthcheck definido) |
| tiendatech-crdb-2 | Up | — (sin healthcheck definido) |
| tiendatech-crdb-3 | Up | — (sin healthcheck definido) |
| tiendatech-gateway | Up | — (sin healthcheck definido, ver nota) |
| tiendatech-inventario | Up | — (sin healthcheck definido, ver nota) |
| tiendatech-jaeger | Up | — (sin healthcheck definido) |
| tiendatech-ordenes-proveedores | Up | healthy |
| tiendatech-pedidos | Up | healthy |
| tiendatech-productos | Up | — (sin healthcheck definido, ver nota) |
| tiendatech-prometheus | Up | healthy |
| tiendatech-usuarios | Up | healthy |
| tiendatech-ventas | Up | healthy |

## Estado del clúster CockroachDB

```powershell
docker compose exec tiendatech-crdb-1 cockroach node status --insecure --host=localhost:26257
```

```text
  id |         address         |       sql_address       |  build  |              started_at              |              updated_at              | locality | is_available | is_live
-----+-------------------------+-------------------------+---------+--------------------------------------+--------------------------------------+----------+--------------+----------
   1 | tiendatech-crdb-1:26257 | tiendatech-crdb-1:26257 | v23.2.4 | 2026-09-04 04:39:00.194102 +0000 UTC | 2026-09-04 04:53:03.153864 +0000 UTC |          | true         | true
   2 | tiendatech-crdb-3:26257 | tiendatech-crdb-3:26257 | v23.2.4 | 2026-09-04 04:39:01.145819 +0000 UTC | 2026-09-04 04:53:04.091308 +0000 UTC |          | true         | true
   3 | tiendatech-crdb-2:26257 | tiendatech-crdb-2:26257 | v23.2.4 | 2026-09-04 04:39:01.180684 +0000 UTC | 2026-09-04 04:53:04.120167 +0000 UTC |          | true         | true
(3 rows)
```

Los tres nodos arrancaron y se unieron al clúster (`started_at` a las 04:39
UTC) sin el error de desfase de reloj; los tres reportan `is_available` e
`is_live` en `true` catorce minutos después.

## Verificación de extremo a extremo (Gateway)

```powershell
curl.exe --fail http://localhost:8180/actuator/health
```

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

(El primer intento con `curl --fail ...` falló por el alias de PowerShell hacia
`Invoke-WebRequest`, no por un problema del sistema; `curl.exe` invoca el curl
real incluido en Windows 10+.)

## Auditoría estática (`scripts/audit_paso4.py`)

Cuarta comprobación del README, ejecutada por Jhinson directamente sobre su
árbol de archivos real (no una copia). En PowerShell, `python3` no estaba
registrado (`no se encontró Python`); el lanzador de Windows `py` sí funcionó:

```powershell
py scripts/audit_paso4.py
```

```json
{
  "warning": "Static candidates only; runtime verification is still required.",
  "cross_schema_candidates": [],
  "cross_schema_foreign_keys": [],
  "dockerfile_stage_counts": {
    "services/armado-ia/Dockerfile": 2,
    "services/inventario-service/Dockerfile": 2,
    "services/ordenes-proveedores-service/Dockerfile": 2,
    "services/pedidos-service/Dockerfile": 2,
    "services/productos-service/Dockerfile": 2,
    "services/usuarios/Dockerfile": 2,
    "services/ventas-service/Dockerfile": 2,
    "Apps/web/frontend/Dockerfile": 3
  },
  "compose_variables_missing_from_env_example": [],
  "openapi_yaml_files": [
    "docs\\api\\armado-ia.yaml",
    "docs\\api\\inventario.yaml",
    "docs\\api\\ordenes-proveedores.yaml",
    "docs\\api\\pedidos.yaml",
    "docs\\api\\productos.yaml",
    "docs\\api\\usuarios.yaml",
    "docs\\api\\ventas.yaml"
  ]
}
```

Sin candidatos de referencia cruzada de esquema, sin claves foráneas cruzadas,
y sin variables de `docker-compose.yml` faltantes en `.env.example` (la
brecha de `EXPERIMENT_FAULT_INJECTION_ENABLED`/`EXPERIMENT_FAULT_DELAY_MS`/
`EXPERIMENT_OMISSION_DELAY_MS`/`HTTP_CLIENT_READ_TIMEOUT_MS` detectada antes
de esta corrida ya no aparece).

## Conclusión

Las cuatro comprobaciones del README quedan hechas por Jhinson sobre su propio
árbol de archivos, no delegadas: el bloqueo de arranque completo (P3) queda
resuelto con `--max-offset=5s`, verificado desde volúmenes Docker limpios (los
13 servicios llegan a `Up`, el clúster CockroachDB de 3 nodos queda disponible
y vivo, `crdb-init` aplica el esquema con éxito), el Gateway responde `UP` en
`/actuator/health` de punta a punta, y la auditoría estática no reporta
hallazgos.

## Pendiente

1. **El fix vive solo en el `docker-compose.yml` local, sin commitear.**
   Está en el mismo archivo que las adiciones aún no terminadas del Paso 10
   (servicio `tiendatech-jaeger` y sus `depends_on`), que el equipo decidió no
   commitear todavía. Mientras no se resuelva esa mezcla, nadie más en el
   equipo — ni el docente — puede reproducir este arranque limpio desde
   `main`. Alternativas: (a) separar por líneas solo el cambio de
   `--max-offset=5s` en un commit propio, dejando el resto de Paso 10 sin
   commitear; o (b) esperar a cerrar Paso 10 y commitear ambos cambios juntos.
   Decisión pendiente del equipo, no técnica.
2. `tiendatech-productos`, `tiendatech-inventario` y `tiendatech-gateway` no
   tienen bloque `healthcheck:` en `docker-compose.yml`, a diferencia de los
   otros 6 servicios con lógica propia. No bloquea el arranque, pero deja a
   Docker sin poder reportar su salud igual que al resto.
