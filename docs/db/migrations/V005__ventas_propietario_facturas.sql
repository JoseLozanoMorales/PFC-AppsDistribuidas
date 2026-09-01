-- Ventas conserva orden_id como referencia de negocio, no como FK física.
-- El snapshot de la orden llega por HTTP desde pedidos-service; por tanto,
-- ventas-service no necesita consultar ni acoplarse al esquema pedidos.
ALTER TABLE ventas.factura_encabezado
    DROP CONSTRAINT IF EXISTS fk_factura_orden;

-- Verificación: debe devolver cero filas.
SELECT constraint_name
FROM information_schema.table_constraints
WHERE table_schema = 'ventas'
  AND table_name = 'factura_encabezado'
  AND constraint_name = 'fk_factura_orden';
