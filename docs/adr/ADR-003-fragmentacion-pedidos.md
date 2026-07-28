# ADR-003: Fragmentación temporal de pedidos

- Estado: aceptada para la Entrega 3
- Fecha: 2026-07-28
- Participación: decisión revisable conjuntamente por los cuatro integrantes

## Contexto

`pedidos.orden` es el agregado transaccional que conecta usuario, compra,
factura e inventario. El código vigente consulta pedidos individuales,
historiales por `usuario_id` y rangos de fechas para reportes. La guía de E3
exige fragmentación horizontal trimestral y colocación orientada al cliente.
En la implementación actual, el atributo temporal se llama `fecha`; corresponde
a `fecha_pedido` en la guía.

## Decisión

Se fragmentan `pedidos.orden` y `pedidos.detalle_orden` mediante rangos
trimestrales de `fecha`. Ambas tablas conservan los mismos límites temporales
para reducir rangos examinados y mantener alineados encabezados y detalles.
La versión comunitaria de CockroachDB 23.2.4 rechaza `PARTITION BY RANGE`
porque lo considera una capacidad Enterprise. Para conservar reproducibilidad
sin una licencia externa, los límites se materializan mediante `SPLIT AT` y se
distribuyen mediante `SCATTER`.

La clave primaria de la orden será `(fecha, orden_id)`. El detalle transportará
la misma fecha y utilizará la clave foránea `(fecha, orden_id)`. Para el patrón
cliente-pedidos se crea el índice `(usuario_id, fecha DESC)` con las columnas
necesarias para resolver el historial sin volver a la tabla principal.

La colocación se interpreta como afinidad de acceso, no mediante
`INTERLEAVE IN PARENT`: esa característica fue retirada de versiones modernas
de CockroachDB. El índice por usuario y fecha proporciona una alternativa
compatible con CockroachDB 23.2+ y será evaluado con `EXPLAIN ANALYZE`.

## Alternativas consideradas

1. Fragmentar por `usuario_id`: favorece el historial individual, pero perjudica
   las consultas trimestrales y puede producir distribución desigual.
2. Fragmentar por categoría de producto: requiere atravesar el detalle y no
   corresponde al agregado principal.
3. No fragmentar: simplifica el esquema, pero incumple el objetivo de E3 y
   obliga a recorrer todos los rangos para el análisis temporal.
4. Usar `INTERLEAVE IN PARENT`: descartado por incompatibilidad con la versión
   obligatoria actual.

## Consecuencias

- Las consultas trimestrales pueden podar particiones.
- Los rangos trimestrales son verificables mediante `SHOW RANGES`, aunque no
  aparecen como particiones Enterprise.
- El historial por usuario dispone de un índice específico.
- Las referencias a una orden deben transportar también `fecha`.
- La aplicación debe adaptar las búsquedas que hoy identifican una orden solo
  por `orden_id`.
- Deben añadirse nuevas particiones antes de cada año operativo.

## Evidencia requerida

- `docs/db/schema.sql` aplicado sin errores.
- `SHOW RANGES FROM TABLE pedidos.orden`.
- `EXPLAIN ANALYZE` de Top-N trimestral e historial por usuario.
- Comparación con una tabla mono-nodo equivalente usando el mismo dataset.
