-- Dataset sintético determinista del catálogo de productos (Entrega 4, Paso 5.3).
--
-- docs/db/product-management-reference.sql deja categorías, marcas, gamas e IVA
-- cargados, pero declara explícitamente "no inserta productos". Sin productos
-- reales, el catálogo tenía solo 4 filas (verificado el 2026-09-01), volumen
-- insuficiente para demostrar una fragmentación por categoría con sentido.
--
-- Este archivo no inventa una cantidad arbitraria por categoría: la deriva del
-- campo productos.categoria_producto.peso_presupuesto, que ya existía en el
-- esquema (pensado originalmente para el motor de recomendación armado-ia) y
-- ya pesa más a CPU/GPU y menos a Periféricos/Accesorios. Fórmula por
-- categoría: 5 productos base + round(100 * peso_presupuesto). Como los diez
-- pesos ya suman 1.00 exactamente, el total es 50 + 100 = 150 productos.
--
-- Datos declarados sintéticos (nombres "... Sintético N"), deterministas
-- (misma semilla produce la misma secuencia) e idempotentes (ON CONFLICT).

USE tiendatech;

INSERT INTO productos.producto
    (producto_id, nombre, preciounitario, stock, marca_id, gama_id, iva_id, costo, categoria_id, habilitado)
SELECT
    20000 + row_number() OVER (ORDER BY c.id_categoria, g),
    c.nombre || ' Sintético ' || g::STRING,
    (20 + ((c.id_categoria * 7 + g) % 180))::DECIMAL(18,2),
    50 + ((c.id_categoria * 3 + g) % 100),
    1 + ((c.id_categoria + g) % 20),
    1 + (g % 4),
    CASE WHEN g % 10 = 0 THEN 2 ELSE 1 END,
    (15 + ((c.id_categoria * 5 + g) % 150))::DECIMAL(18,2),
    c.id_categoria,
    true
FROM productos.categoria_producto c
CROSS JOIN LATERAL generate_series(1, 5 + round(100 * c.peso_presupuesto)::INT) AS serie(g)
ON CONFLICT (producto_id) DO NOTHING;
