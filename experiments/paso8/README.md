# Paso 8 - ejecución y análisis del experimento

> **Alcance vigente:** la evidencia principal del Paso 8 es la campaña contra
> microservicios y CockroachDB conservada en
> `resultados-reales/oficial-v4-20260904/`. Este documento describe además el
> piloto local histórico. `run_paso8.py` y `coordination_lab.py` usan SQLite y
> se conservan únicamente para reproducibilidad; no sustentan C2, C3 ni C6.

La campaña real ejecutó 120 corridas (24 condiciones × 5 repeticiones), con
60 segundos de calentamiento y 90 de medición. Su informe y sus límites están
en `resultados-reales/oficial-v4-20260904/analisis/informe_final.md`.

El piloto local ejecuta el experimento propio de TiendaTech sobre el banco construido
en `experiments/paso7`: confirmacion en dos fases (`2pc`) frente a saga con
compensacion (`saga`), bajo fallos de pasarela.

## Matriz local histórica

- Estrategias: `2pc`, `saga`.
- Concurrencia: `50`, `100`, `200`, `400` compradores simultaneos.
- Fallos de pasarela: `none`, `omission`, `timing`.
- Repeticiones: `12` por condicion.
- Total: `2 x 4 x 3 x 12 = 288` corridas.
- Probabilidad de fallo: `0.10`.

Comando usado para la corrida local:

```powershell
py experiments/paso8/run_paso8.py `
  --output experiments/paso8/resultados `
  --repeticiones 12 `
  --concurrencias 50 100 200 400 `
  --fault-probability 0.10 `
  --delay-seconds 5 `
  --warmup-seconds 60
```

El ejecutor usa por defecto la temporización de cinco segundos y sesenta
segundos de carga real descartada. El calentamiento se ejecuta sobre una base
separada para que sus operaciones no contaminen la corrida medida.

```powershell
py experiments/paso8/run_paso8.py `
  --output experiments/paso8/resultados-rubrica `
  --repeticiones 12 `
  --concurrencias 50 100 200 400 `
  --fault-probability 0.10 `
  --delay-seconds 5 `
  --warmup-seconds 60
```

## Evidencia inicial (no sustituye la nueva corrida)

- `resultados/experimento_crudo.csv`: 120 corridas iniciales; deben regenerarse
  las 288 corridas con la configuración corregida.
- `resultados/experimento_resumen.csv`: 24 condiciones con mediana, IC95%,
  tasas e intervalos binomiales.
- `resultados/comparaciones_mann_whitney.csv`: comparacion 2PC vs Saga por
  condicion, con U de Mann-Whitney y A12 de Vargha-Delaney.
- `resultados/compatibilidad_resultados.csv`: 120 casos del asistente de
  compatibilidad con falsos positivos y falsos negativos.
- `resultados/compatibilidad_resumen.json`: aciertos e IC95% binomial.
- `resultados/boxplot_latencia_p95.svg` y `resultados/boxplot_throughput.svg`.
- `resultados/amenazas_validez.md`: cuatro categorias de amenazas.
- `resultados/db/*.db`: base SQLite auditable por corrida.

## Validación principal contra microservicios reales

`run_real_experiment.py` ejecuta compradores sintéticos contra
`Gateway -> Pedidos -> Ventas/Inventario -> CockroachDB`. Antes de medir comprueba
que los seis componentes estén disponibles y realiza 60 segundos de calentamiento
de conectividad descartado. El banco JSON no se versiona porque contiene JWT efímeros; cada
caso requiere `caseId`, `token`, `direccionId` y `metodopagoId`, y debe corresponder
a un usuario sintético con carrito preparado.

```bash
python3 experiments/paso8/run_microservices.py \
  --admin-token "$ADMIN_JWT" \
  --request-bank /tmp/compradores-sinteticos.json \
  --concurrencia 50 --repeticion 1 --failure-mode timing
```

Para habilitar los fallos controlados, el stack experimental se levanta con
`EXPERIMENT_FAULT_INJECTION_ENABLED=true`. `timing` retrasa la respuesta cinco
segundos; `omission` excede el timeout de siete segundos del cliente y no devuelve
una respuesta útil. El mecanismo permanece desactivado por defecto.

La campaña oficial ya conservada comprende 120 corridas y valida que todas las
condiciones y concurrencias se ejecutaron. Registró saturación extrema y solo
tres checkouts confirmados; por ello demuestra ejecución distribuida real, pero
no permite afirmar superioridad de 2PC o Saga ni invariantes con potencia suficiente.

## Resultados de la corrida inicial invalidada

- El oraculo de consistencia pasó en las 120 corridas iniciales: no se observaron pagos
  incompletos, descuentos dobles, stock negativo ni compensaciones incompletas.
- La tasa de inconsistencia observable fue `0.0` en todas las condiciones, pero
  ese valor no es una conclusión vigente porque el candado global impedía medirla.
- Con fallo `omission` y `timing`, la tasa de abortos se mantuvo cerca de la
  probabilidad de fallo configurada (`0.10`), con variacion esperada por semilla.
- En todas las comparaciones de latencia p95, `2pc` tuvo menor latencia que
  `saga` en esta implementacion local del banco (`p_aprox=0.009023`,
  `A12=0.0` para 2PC mayor que Saga).
- En el banco de compatibilidad se evaluaron 120 casos: 120 aciertos, 0 falsos
  positivos y 0 falsos negativos; IC95% binomial de exactitud `[0.968981, 1.0]`.

Estos valores se conservan solamente como trazabilidad. La conclusión final se
redactará después de generar y analizar las 288 corridas corregidas.
