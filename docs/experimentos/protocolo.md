# Protocolo experimental del pipeline analítico

## Objetivo

Comparar el tiempo de ejecución del mismo análisis sobre un conjunto determinista
de **600 000 órdenes y 600 000 detalles**, usando pandas como baseline y PySpark
en modo local con `N ∈ {1, 2, 4, 8}` workers.

El universo se delimita mediante `orden_id BETWEEN 1 AND 600000`. Las dos órdenes
transaccionales creadas durante las pruebas funcionales no forman parte del
experimento.

## Transformaciones

Se aplican exactamente cinco etapas lógicas equivalentes en ambos motores:

1. **T1 — temporal:** conversión de `fecha` y derivación del trimestre.
2. **T2 — filtro:** año 2026, órdenes no canceladas, cantidades positivas y
   usuarios habilitados.
3. **T3 — joins:** unión de orden, detalle y usuario.
4. **T4 — agregación con ventana:** unidades por producto y ranking top-10 por
   trimestre; también se agregan frecuencia y gasto por cliente.
5. **T5 — ML:** `Bucketizer` de gasto en BRONCE, PLATA, ORO y PLATINO, con
   cortes fijos derivados de los cuartiles del dataset determinista.

Los resultados se escriben en Parquet:

- `top_productos`;
- `segmentos_clientes`.

## Variables

- Variable independiente: número de workers PySpark (`1`, `2`, `4`, `8`) o
  motor pandas.
- Variables dependientes: duración total, CPU consumida y memoria RSS final.
- Variables controladas: misma computadora, mismo dataset, misma consulta JDBC,
  misma versión de Java/Python, mismo top-10 y ningún otro proceso intensivo.

## Procedimiento reproducible

1. Levantar los tres nodos CockroachDB y ejecutar `crdb-seed`.
2. Confirmar 600 000 órdenes y detalles analíticos.
3. Crear un entorno Python e instalar `spark/requirements.txt`. En Windows,
   ejecutar PySpark mediante `spark/ejecutar-pyspark.ps1`; este usa la imagen
   oficial Linux de Apache Spark y evita la dependencia no portable
   `winutils.exe`.
4. Ejecutar una vez pandas y PySpark; validar sus Parquet con
   `spark/validar_resultados.py`.
5. Ejecutar `spark/experimento.py --repeticiones 10 --incluir-pandas`.
6. Para cada configuración descartar la primera y la última medición.
7. Calcular media, desviación estándar e intervalo de confianza del 95 % sobre
   las ocho mediciones restantes.
8. Comparar observaciones emparejadas. Aplicar Shapiro-Wilk a las diferencias:
   si `p ≥ 0.05`, usar t pareada; en caso contrario, Wilcoxon.
9. Conservar `mediciones.csv`, `resumen.json`, Parquet, versiones del entorno y
   capturas del Administrador de tareas como evidencia.

Ejemplo desde la raíz del repositorio:

```powershell
python spark/baseline.py --overwrite
powershell -ExecutionPolicy Bypass -File spark/ejecutar-pyspark.ps1 -Workers 4 -Salida pyspark
python spark/validar_resultados.py
python spark/experimento.py --repeticiones 10 --workers 1 2 4 8 --incluir-pandas
```

## Amenazas a la validez

### Internas

1. Cachés del sistema operativo y de CockroachDB pueden favorecer ejecuciones
   posteriores; por ello se descartan primera y última repetición.
2. Procesos en segundo plano pueden alterar CPU y memoria; se ejecutará sin otras
   cargas intensivas y se registrará el entorno.
3. La lectura JDBC puede introducir variación de red local y del clúster; se
   mantendrán los tres nodos saludables y la misma configuración.
4. La recolección de métricas añade una sobrecarga pequeña; se utiliza el mismo
   mecanismo en todas las repeticiones.

### Externas

1. El dataset es sintético y su distribución puede no representar una tienda
   real con estacionalidad, productos populares o varios detalles por orden.
2. El modo `local[N]` evalúa paralelismo en una sola computadora y no permite
   extrapolar directamente los resultados a un clúster Spark multinodo.
3. Los resultados dependen del hardware y las versiones instaladas; por ello se
   publicarán junto con la configuración utilizada.

## Criterios de aceptación

- 600 000 filas de orden y detalle leídas por cada motor.
- Igualdad numérica de ambas salidas dentro de tolerancia `1e-6`.
- Diez repeticiones por configuración y ocho válidas después del descarte.
- Media, desviación, IC95 % y prueba pareada presentes en `resumen.json`.
