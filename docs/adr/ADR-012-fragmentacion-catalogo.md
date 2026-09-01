# ADR-012: Fragmentación del catálogo de productos por categoría

- Estado: aceptada para la Entrega 4
- Fecha: 2026-09-01
- Participación: decisión revisable conjuntamente por los cuatro integrantes

## Contexto

La Entrega 1 no fragmentó el catálogo. La guía de la Entrega Final (Paso 5.3) pide
documentar la fragmentación del catálogo por categoría o marca, con el mismo rigor
aplicado a pedidos en `ADR-003-fragmentacion-pedidos.md`. Al revisar el catálogo real
el 1 de septiembre de 2026, se encontró que `productos.producto` solo tenía cuatro
filas cargadas — volumen insuficiente para demostrar una fragmentación con sentido:
`SPLIT AT` sobre cuatro filas habría producido rangos casi todos vacíos.

Se generó un dataset sintético determinista de 150 productos
(`docs/db/seed-catalogo-sintetico.sql`), distribuido proporcionalmente al campo
`productos.categoria_producto.peso_presupuesto` que ya existía en el esquema —pensado
originalmente para el motor de recomendación `armado-ia`—, para no inventar una
distribución arbitraria de productos por categoría. Los datos están declarados como
sintéticos en el propio nombre (`... Sintético N`) y en el encabezado del script.

## Decisión

Se fragmenta `productos.producto` por `categoria_id`, usando el índice secundario que
ya existía, `idx_producto_categoria (categoria_id, habilitado)`, mediante
`ALTER INDEX ... SPLIT AT VALUES (2), (3), ..., (10)` seguido de `SCATTER`, en vez de
modificar la clave primaria de la tabla.

## Alternativas consideradas

1. Reordenar la clave primaria de `producto` a `(categoria_id, producto_id)`: descartada
   porque rompería las referencias por `producto_id` desde `carrito_detalle`,
   `galeria_productos_v2`, `inventario` y otras tablas que ya dependen de ese
   identificador — exactamente el tipo de cambio riesgoso que el equipo decidió evitar
   a esta altura del proyecto (`docs/plan-cierre-e4.md`).
2. Usar `PARTITION BY RANGE`: descartada por la misma razón que en ADR-003 — es una
   capacidad Enterprise, no disponible en la versión comunitaria de CockroachDB 23.2.4
   que usa el equipo.
3. No fragmentar el catálogo, solo documentar honestamente su ausencia: era la
   recomendación inicial, dado el bajo volumen real de productos. Se descartó cuando el
   equipo decidió generar el dataset sintético para cumplir la guía de forma completa.

## Consecuencias

- El catálogo quedó fragmentado en diez rangos físicos, uno por categoría, verificado
  con `SHOW RANGES FROM INDEX productos.producto@idx_producto_categoria WITH DETAILS`
  (`docs/evidencias/rangos-catalogo-e4.txt`): los `key_count` de cada rango (17, 28, 11,
  13, 15, 29, 14, 15, 7, 5) coinciden exactamente con el conteo real de productos por
  categoría.
- El factor de replicación 3 se mantiene en los diez fragmentos
  (`voting_replicas={1,2,3}` en todos).
- A diferencia de `pedidos.orden`, la fragmentación del catálogo no está atada a ningún
  patrón de acceso recurrente del negocio (no hay reportes trimestrales de catálogo);
  su propósito es cumplir el requisito formal de la guía y demostrar el mecanismo, no
  resuelve un problema de escala real todavía — un catálogo de 154 filas no lo necesita
  hoy. Se documenta esta honestidad en vez de presentar la fragmentación como una
  necesidad operativa que no existe.

## Evidencia requerida

- `docs/db/seed-catalogo-sintetico.sql` aplicado sin errores (150 filas insertadas).
- `docs/evidencias/rangos-catalogo-e4.txt` con la salida de `SHOW RANGES` tras
  `SPLIT AT` + `SCATTER`.
