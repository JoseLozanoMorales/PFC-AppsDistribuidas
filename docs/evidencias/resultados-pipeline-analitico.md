# Evidencia del pipeline analítico

Fecha de ejecución: 2026-07-29  
Equipo de ejecución: computadora local del equipo, Docker Desktop sobre Windows  
Dataset: 600 000 órdenes, 600 000 detalles y 10 000 usuarios

## Validación funcional

- Órdenes leídas por pandas y PySpark: 600 000.
- Detalles leídos por pandas y PySpark: 600 000.
- Filas resultantes del join después de filtros: 570 000.
- Resultado top-10: 40 filas, diez por cada trimestre.
- Clientes segmentados: 9 500.
- Igualdad pandas/PySpark: validada en ambos Parquet con tolerancia numérica
  absoluta de `1e-6`.
- Distribución: BRONCE 2 376, PLATA 2 376, ORO 2 374 y PLATINO 2 374.

## Resultados temporales

Se realizaron diez repeticiones por configuración. Según el protocolo, se
descartaron la primera y la última y se analizaron ocho observaciones.

| Configuración | n | Media (s) | Desv. (s) | IC95 % (s) |
|---|---:|---:|---:|---:|
| PySpark `local[1]` | 8 | 17.055 | 0.822 | [16.368, 17.743] |
| PySpark `local[2]` | 8 | 16.771 | 1.642 | [15.399, 18.144] |
| PySpark `local[4]` | 8 | 17.000 | 0.625 | [16.478, 17.522] |
| PySpark `local[8]` | 8 | 17.448 | 1.239 | [16.412, 18.484] |
| pandas | 8 | 2.744 | 0.158 | [2.613, 2.876] |

## Pruebas pareadas

- `local[1]` frente a `local[2]`: Shapiro-Wilk `p=0.2109`; t pareada
  `p=0.7074`.
- `local[1]` frente a `local[4]`: Shapiro-Wilk `p=0.5211`; t pareada
  `p=0.9028`.
- `local[1]` frente a `local[8]`: Shapiro-Wilk `p=0.0443`; Wilcoxon `p=1.0000`.
- `local[1]` frente a pandas: Shapiro-Wilk `p=0.8570`; t pareada `p<0.0001`.

Con `α=0.05`, no se encontró una diferencia significativa entre las
configuraciones locales de PySpark. pandas fue significativamente más rápido en
este dataset y equipo. El resultado es coherente con un volumen que cabe en
memoria y con el coste fijo de iniciar Spark y leer mediante JDBC; no demuestra
que pandas escale mejor ante datasets que excedan la memoria o ante un clúster
distribuido.

## Artefactos generados

La ejecución completa produjo:

- `spark/out/experimento/mediciones.csv`: cincuenta mediciones crudas;
- `spark/out/experimento/resumen.json`: estadísticos y pruebas;
- un directorio Parquet por configuración y repetición;
- `spark/out/pandas` y `spark/out/pyspark`: resultados usados en la validación
  de igualdad.

Los valores de CPU y RSS registrados por PySpark corresponden al proceso
controlador Python dentro del contenedor; el tiempo de pared es la métrica
principal y sí abarca todo el trabajo del job.
