# Paso 8 — informe de ejecución real

## Protocolo ejecutado

- 24 condiciones: 2 estrategias (`2pc`, `saga`) × 4 niveles de concurrencia
  (50, 100, 200, 400) × 3 modos (`none`, `omission`, `timing`).
- 5 repeticiones por condición: 120 corridas.
- Cada corrida: 60 s de calentamiento descartado y 90 s de medición.
- `delay-seconds=5`, probabilidad de fallo 0.10 y reinicio de los seis servicios
  de aplicación entre corridas.
- Orden por repetición completa y escritura incremental reanudable.
- CPU y memoria del proceso Locust registradas por corrida.

La guía sugería corridas de cinco minutos. Se usaron corridas de 2m30s por la
ventana de ejecución disponible: 60 s de calentamiento y 90 s de medición. Esta
es una desviación deliberada y una amenaza a la validez.

## Validación estructural

- 120 filas y 120 claves de corrida únicas.
- 24 condiciones, todas con exactamente 5 repeticiones.
- Ninguna corrida con cero solicitudes.
- Todas las corridas alcanzaron la concurrencia objetivo declarada por Locust.
- Las 120 filas registran los mismos parámetros: warmup 60 s, medición 90 s y
  temporización 5 s.

## Resultado y limitación principal

En total se registraron 76 022 solicitudes, de las cuales 56 353 fallaron. Se
alcanzó el paso de checkout en 401 ocasiones: 398 fallaron y solo 3 fueron
confirmadas, todas en una única corrida (`timing`, `saga`, concurrencia 100,
repetición 4). La mediana de checkouts confirmados es cero en las 24
condiciones.

Por tanto, el experimento documenta de manera válida el comportamiento de
saturación/fallo extremo del stack bajo estos niveles y esta máquina de carga,
pero no aporta suficientes checkouts exitosos para sostener una comparación
robusta del rendimiento del flujo de compra entre 2PC y Saga. No debe
interpretarse la ausencia de confirmaciones como equivalencia entre las dos
estrategias.

## Interrupciones

La ejecución se reanudó desde checkpoints incrementales tras interrupciones de
energía/conectividad. No se duplicaron corridas completas. Los JWT se obtuvieron
mediante login real y solo se reutilizaron si su expiración cubría calentamiento,
medición y 60 s adicionales de margen.
