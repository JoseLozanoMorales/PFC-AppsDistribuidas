# Integración del servicio Pedidos con CockroachDB

- Fecha: 2026-07-28
- Servicio: `pedidos-crdb-service`
- Profile Spring: `crdb`
- Puerto local: 8183
- Cluster: `crdb-1`, `crdb-2`, `crdb-3`

## Configuración

El profile `crdb` externaliza la conexión mediante:

```text
CRDB_DATASOURCE_URL
CRDB_DATASOURCE_USERNAME
CRDB_DATASOURCE_PASSWORD
```

La conexión utilizada en Compose fue:

```text
jdbc:postgresql://crdb-1:26257/tiendatech?sslmode=disable
```

El pool Hikari se configuró con aislamiento
`TRANSACTION_SERIALIZABLE`. El método que persiste la orden también declara
explícitamente `Isolation.SERIALIZABLE`.

## Reintentos de serialización

`CrdbRetryExecutor` inspecciona la cadena de causas y reintenta únicamente
errores con SQLSTATE `40001`. El número máximo de intentos y el retardo se
configuran mediante variables de entorno.

Las pruebas se ejecutaron dentro de Maven 3.9 con Java 21:

```text
CrdbRetryExecutorTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

CockroachDbIntegrationTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

Las pruebas confirman que:

- SQLSTATE `40001` se reconoce como reiniciable;
- un error de conexión `08001` no se reintenta.
- Testcontainers puede iniciar CockroachDB 23.2.4 desde un entorno vacío;
- el driver PostgreSQL establece una transacción `SERIALIZABLE`;
- una escritura y lectura real se confirman dentro del contenedor temporal.

## Comprobación REST

`GET http://localhost:8183/health` devolvió:

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL"
      }
    }
  }
}
```

El controlador PostgreSQL identifica a CockroachDB por el protocolo compatible.

`GET http://localhost:8183/api/ordenes/1` consultó el dataset distribuido:

```json
{
  "ordenId": 1,
  "usuarioId": 1,
  "direccionId": 1,
  "metodopagoId": 1,
  "subtotal": 10.01,
  "total": 11.51,
  "fecha": "2026-01-02"
}
```

## Métricas Prometheus

El endpoint `GET /actuator/prometheus` expone únicamente los instrumentos incrementales
exigidos para CockroachDB:

```text
crdb_query_duration_seconds
crdb_transaction_retries_total
crdb_pool_active_connections
```

Después de tres consultas REST:

```text
crdb_query_duration_seconds_count 3
crdb_query_duration_seconds_sum 0.022559737
crdb_transaction_retries_total 0.0
crdb_pool_active_connections 0.0
```

El valor cero de reintentos es correcto: durante esta comprobación no se produjo
una colisión serializable.

La salida completa se validó con `promtool` 2.54.1:

```text
Total cardinality: 74
Exit code: 0
```

## Compatibilidad transaccional de extremo a extremo

Se incorporaron al esquema distribuido las tablas de carrito y factura. Para no
romper el sistema heredado, Facturación selecciona la implementación por perfil:

- perfil normal: conserva los procedimientos almacenados de PostgreSQL;
- perfil `crdb`: usa SQL portable y transacciones `SERIALIZABLE`.

El dataset analítico ocupa los identificadores de orden `1..600000`. La
secuencia transaccional se sincroniza idempotentemente con el mayor ID existente
y reserva como mínimo el rango desde `1000001`, evitando colisiones al reaplicar
el esquema.

El 2026-07-28 se ejecutó por REST el flujo real:

```text
GET  /api/carrito/2
POST /api/carrito/1/agregar        productoId=4, cantidad=1
POST /api/ordenes/checkout         usuarioId=2, direccionId=8, metodopagoId=1
```

Resultado devuelto por `pedidos-crdb-service`:

```json
{
  "ordenId": 1000002,
  "usuarioId": 2,
  "direccionId": 8,
  "metodopagoId": 1,
  "subtotal": 214.99,
  "total": 214.9900,
  "fecha": "2026-07-28"
}
```

Comprobación directa en CockroachDB:

```text
orden_id  fecha       usuario_id  detalles  factura_id  numero
1000002   2026-07-28  2           1         2           FAC-E3-1000002

items_carrito_restantes
0
```

Esto demuestra que la orden y su detalle se confirmaron en CockroachDB, el
carrito se vació, Facturación leyó la orden ya confirmada, generó la factura y
comunicó la salida a Inventario.

## Alcance pendiente

- El vídeo de tolerancia a fallos permanece pendiente de grabación.

La colisión serializable y el incremento real del contador quedaron registrados
en `docs/evidencias/colision-serializable-controlada.md`.
