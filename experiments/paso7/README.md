# Paso 7 — banco de pruebas de coordinación

Este banco compara **E-2PC** y **E-SAGA** sobre el mismo programa. La selección se
hace exclusivamente con `COORD=2pc|saga`; no existen ramas de código divergentes.
Usa una base SQL aislada por ejecución para representar los datos propiedad de
Inventario, Pagos y Órdenes, sin alterar los esquemas productivos del paso 4.

## Instrumentos

- `FaultInjector`: intermediario de la pasarela simulada. `omission` pierde la
  respuesta y `timing` la entrega después de 5 s (configurable para pruebas rápidas).
- Generador concurrente: compradores compiten por el producto 1. Semilla y casos
  iguales generan la misma carga; las operaciones se identifican de forma única.
- Oráculo: emite sí/no para pago exacto, descuento único, ausencia histórica de
  stock negativo, correspondencia entre el stock final y todos sus movimientos,
  y compensación completa. Esta correspondencia permite detectar actualizaciones
  perdidas bajo concurrencia aunque el stock nunca llegue a ser negativo.
- Bancos: cada piloto escribe 120 transacciones y 120 configuraciones de PC con
  respuesta conocida (socket, RAM y margen de fuente) para el asistente del paso 8.

## Ejecución

```bash
cd experiments/paso7
python3 -m unittest -v test_coordination_lab.py
COORD=2pc python3 coordination_lab.py --cases 30
COORD=saga python3 coordination_lab.py --cases 30
# Piloto comparativo completo en una orden:
python3 coordination_lab.py --coords 2pc saga --cases 30 --seed 2026
```

La evidencia comparativa versionada se regeneró sin candado global con:

```bash
python3 experiments/paso7/coordination_lab.py \
  --coords 2pc saga \
  --cases 120 \
  --seed 2026 \
  --workers 12 \
  --initial-stock 500 \
  --fault-probability 0.35 \
  --delay-seconds 0.01 \
  --output experiments/paso7/evidence
```

En esta corrida, `oracle-2pc.json` pasa los cinco invariantes. En
`oracle-saga.json`, `stock_cuadra_con_movimientos` detecta una violación. Esta
diferencia confirma que los compradores sobre el mismo producto se solapan de
verdad: 2PC serializa mediante `BEGIN IMMEDIATE`, mientras Saga puede observar
un stock anterior y perder una actualización. El retardo reducido solo agiliza
este piloto; no sustituye la configuración de cinco segundos del Paso 8.

Los artefactos quedan en `evidence/`: `pilot.csv`, bases auditables,
`oracle-2pc.json`, `oracle-saga.json`, `transaction-case-bank.json` y
`compatibility-case-bank.json`. El retardo predeterminado
es realmente 5 s; `--delay-seconds 0.01` se reserva para CI/pruebas rápidas.
