# Evidencia principal de C2, C3 y C6

La campaña canónica del Paso 8 es
`resultados-reales/oficial-v4-20260904/experimento_real_crudo.csv`.
Fue ejecutada mediante `run_real_experiment.py` contra el API Gateway, los
microservicios de Pedidos, Ventas e Inventario y CockroachDB.

## Estado comprobable

- 120 corridas y 120 claves únicas.
- 24 condiciones con cinco repeticiones cada una.
- Estrategias `2pc` y `saga`.
- Concurrencias 50, 100, 200 y 400, alcanzadas por Locust.
- Modos `none`, `omission` y `timing`.
- 60 segundos de calentamiento y 90 segundos de medición.
- 76 022 solicitudes totales.
- 401 intentos de checkout, 398 fallidos y 3 confirmados.

`analisis/validacion.json` acredita la integridad estructural de la matriz y
`analisis/informe_final.md` conserva la interpretación.

## Límite de inferencia

La campaña prueba que el protocolo distribuido se ejecutó y deja evidencia de
saturación y fallos reales. La cantidad de checkouts confirmados es insuficiente
para atribuir conservación de invariantes o superioridad de rendimiento a una
estrategia. Una repetición futura debe comenzar con concurrencias menores y
escalar después de demostrar un flujo basal saludable.

`coordination_lab.py` y `run_paso8.py` son pilotos locales en SQLite. Su candado
de Python fue eliminado, pero SQLite conserva escritor único; por tanto, esos
resultados no se usan como evidencia de C2, C3 o C6.
