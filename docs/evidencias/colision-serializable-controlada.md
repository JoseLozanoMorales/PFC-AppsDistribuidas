# Evidencia de reintento por colisión serializable

- Fecha: 2026-07-28
- Servicio: `tiendatech-pedidos`
- Endpoint de prueba: `POST /api/crdb/retry-probe`
- Aislamiento: `SERIALIZABLE`
- SQLSTATE esperado: `40001`

## Método

La sonda utiliza una fila exclusiva (`pedidos.retry_probe`) que no pertenece a
los datos de negocio. Dos transacciones leen la misma versión de esa fila. La
transacción competidora escribe y confirma primero; cuando la sonda intenta
escribir desde su versión anterior, CockroachDB rechaza el intento con SQLSTATE
`40001`.

`CrdbRetryExecutor` reconoce ese código, incrementa
`crdb_transaction_retries_total` y vuelve a ejecutar toda la transacción con una
conexión nueva.

El endpoint y sus componentes solo se habilitan con el perfil Spring `crdb`.

## Resultado REST

```json
{
  "sqlStateProvocado": "40001",
  "intentos": 2,
  "reintentosAntes": 0.0,
  "reintentosDespues": 1.0,
  "valorFinal": 2
}
```

El resultado confirma un intento fallido reiniciable y un segundo intento
exitoso.

## Resultado Prometheus

Antes de la prueba:

```text
crdb_transaction_retries_total 0.0
```

Después de la prueba:

```text
crdb_transaction_retries_total 1.0
```

## Comprobación directa en CockroachDB

```text
probe_id  valor
1         2
```

El valor `2` representa dos escrituras confirmadas: una de la transacción
competidora y otra del reintento exitoso. La escritura del intento rechazado no
se aplicó.

## Evidencia visual recomendada

Para una captura compacta, mostrar simultáneamente:

1. la respuesta JSON del `POST /api/crdb/retry-probe`;
2. la línea `crdb_transaction_retries_total 1.0` de `/actuator/prometheus`;
3. opcionalmente, la consulta de CockroachDB que devuelve `probe_id=1, valor=2`.

