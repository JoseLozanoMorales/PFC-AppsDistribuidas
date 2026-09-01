# Paso 8 - ejecucion y analisis del experimento

Este paso ejecuta el experimento propio de TiendaTech sobre el banco construido
en `experiments/paso7`: confirmacion en dos fases (`2pc`) frente a saga con
compensacion (`saga`), bajo fallos de pasarela.

## Matriz ejecutada

- Estrategias: `2pc`, `saga`.
- Concurrencia: `50`, `100`, `200`, `400` compradores simultaneos.
- Fallos de pasarela: `none`, `omission`, `timing`.
- Repeticiones: `5` por condicion.
- Total: `2 x 4 x 3 x 5 = 120` corridas.
- Probabilidad de fallo: `0.10`.

Comando usado para la corrida local:

```powershell
py experiments/paso8/run_paso8.py `
  --output experiments/paso8/resultados `
  --repeticiones 5 `
  --concurrencias 50 100 200 400 `
  --fault-probability 0.10 `
  --delay-seconds 0.05 `
  --warmup-seconds 0
```

La rubrica describe temporizacion de cinco segundos y descarte de sesenta
segundos de calentamiento. La ejecucion local versionada acelera esos dos
parametros para terminar en una ventana razonable de laboratorio; los valores
reales usados quedan en `resultados/metadata.json` y se declaran como amenaza a
la validez interna. Para ejecutar la configuracion literal de rubrica:

```powershell
py experiments/paso8/run_paso8.py `
  --output experiments/paso8/resultados-rubrica `
  --repeticiones 5 `
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
