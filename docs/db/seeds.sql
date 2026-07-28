-- Dataset determinista de referencia para TiendaTech E3.
-- Cardinalidad objetivo: 10 000 usuarios, 500 000 órdenes y 500 000 detalles.
-- La ejecución es idempotente gracias a las claves explícitas y ON CONFLICT.

USE tiendatech;

INSERT INTO usuarios.usuario
    (usuario_id, nombre, correo, habilitado, creado_en)
SELECT
    g,
    'Cliente ' || g::STRING,
    'cliente' || g::STRING || '@tiendatech.local',
    true,
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
FROM generate_series(1, 10000) AS serie(g)
ON CONFLICT (usuario_id) DO NOTHING;

INSERT INTO pedidos.orden
    (fecha, orden_id, usuario_id, direccion_id, metodopago_id,
     subtotal, total, estado, creado_en)
SELECT
    DATE '2026-01-01' + (g % 365)::INT,
    g,
    ((g - 1) % 10000) + 1,
    ((g - 1) % 20000) + 1,
    ((g - 1) % 3) + 1,
    ((g % 99000) + 1000)::DECIMAL / 100,
    (((g % 99000) + 1000)::DECIMAL / 100) * 1.15,
    CASE
        WHEN g % 20 = 0 THEN 'CANCELADA'
        WHEN g % 5 = 0 THEN 'PAGADA'
        ELSE 'FACTURADA'
    END,
    (DATE '2026-01-01' + (g % 365)::INT)::TIMESTAMPTZ
FROM generate_series(1, 500000) AS serie(g)
ON CONFLICT (fecha, orden_id) DO NOTHING;

INSERT INTO pedidos.detalle_orden
    (fecha, detalle_id, orden_id, producto_id, cantidad, precio_unitario,
     subtotal, iva, total)
SELECT
    DATE '2026-01-01' + (g % 365)::INT,
    g,
    g,
    ((g - 1) % 2500) + 1,
    ((g - 1) % 5) + 1,
    ((g % 99000) + 1000)::DECIMAL / 100,
    (((g - 1) % 5) + 1) * (((g % 99000) + 1000)::DECIMAL / 100),
    ((((g - 1) % 5) + 1) * (((g % 99000) + 1000)::DECIMAL / 100)) * 0.15,
    ((((g - 1) % 5) + 1) * (((g % 99000) + 1000)::DECIMAL / 100)) * 1.15
FROM generate_series(1, 500000) AS serie(g)
ON CONFLICT (fecha, detalle_id) DO NOTHING;

-- Verificación reproducible incluida en la salida del cargador.
SELECT 'usuarios' AS entidad, count(*) AS filas FROM usuarios.usuario
UNION ALL
SELECT 'ordenes', count(*) FROM pedidos.orden
UNION ALL
SELECT 'detalles', count(*) FROM pedidos.detalle_orden
ORDER BY entidad;

SELECT
    CASE
        WHEN fecha < DATE '2026-04-01' THEN '2026-Q1'
        WHEN fecha < DATE '2026-07-01' THEN '2026-Q2'
        WHEN fecha < DATE '2026-10-01' THEN '2026-Q3'
        ELSE '2026-Q4'
    END AS trimestre,
    count(*) AS ordenes
FROM pedidos.orden
GROUP BY trimestre
ORDER BY trimestre;
