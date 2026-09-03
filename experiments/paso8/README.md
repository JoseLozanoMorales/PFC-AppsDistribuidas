# Paso 8 - ejecucion y analisis del experimento

Este paso ejecuta el experimento propio de TiendaTech sobre el banco construido
en `experiments/paso7`: confirmacion en dos fases (`2pc`) frente a saga con
compensacion (`saga`), bajo fallos de pasarela.

## Matriz ejecutada

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

## Evidencia generada

- `resultados/experimento_crudo.csv`: 120 corridas completas.
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

## Validación complementaria contra microservicios reales

`run_microservices.py` ejecuta compradores sintéticos contra
`Gateway -> Pedidos -> Ventas/Inventario -> CockroachDB`. Antes de medir comprueba
que los seis componentes estén disponibles y realiza 60 segundos de calentamiento
de conectividad descartado. El banco JSON no se versiona porque contiene JWT efímeros; cada
caso requiere `caseId`, `token`, `direccionId` y `metodopagoId`, y debe corresponder
a un usuario sintético con carrito preparado.

```bash
python3 experiments/paso8/run_microservices.py \
  --admin-token "$ADMIN_JWT" \
  --request-bank /tmp/compradores-sinteticos.json \
  --concurrencia 50 --repeticion 1
```

La salida conserva `trace_id`, estado HTTP, éxito, latencia y error por compra.
Para comparar estrategias hay que reiniciar el stack con cada valor de `COORD` y
verificar en los metadatos cuál quedó activo. El ejecutor no afirma que esa variable
altere el flujo productivo: esa conmutación debe existir en el coordinador real.

## Resultados principales

- El oraculo de consistencia paso en las 120 corridas: no se observaron pagos
  incompletos, descuentos dobles, stock negativo ni compensaciones incompletas.
- La tasa de inconsistencia observable fue `0.0` en todas las condiciones.
- Con fallo `omission` y `timing`, la tasa de abortos se mantuvo cerca de la
  probabilidad de fallo configurada (`0.10`), con variacion esperada por semilla.
- En todas las comparaciones de latencia p95, `2pc` tuvo menor latencia que
  `saga` en esta implementacion local del banco (`p_aprox=0.009023`,
  `A12=0.0` para 2PC mayor que Saga).
- En el banco de compatibilidad se evaluaron 120 casos: 120 aciertos, 0 falsos
  positivos y 0 falsos negativos; IC95% binomial de exactitud `[0.968981, 1.0]`.
