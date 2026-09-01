-- Paso 4: Inventario pasa a ser propietario de stock, costo y reservas.
-- Migración aditiva y repetible: no elimina datos ni columnas de Productos.

ALTER TABLE inventario.inventario_producto ADD COLUMN IF NOT EXISTS nombre STRING;
ALTER TABLE inventario.inventario_producto ADD COLUMN IF NOT EXISTS costo DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE inventario.inventario_producto ADD COLUMN IF NOT EXISTS precio_referencia DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE inventario.inventario_producto ADD COLUMN IF NOT EXISTS habilitado BOOL NOT NULL DEFAULT true;

-- Única lectura cruzada permitida durante la migración de propiedad.
UPSERT INTO inventario.inventario_producto
    (producto_id, nombre, stock, stock_minimo, costo, precio_referencia,
     habilitado, valor_inventario, actualizado_en)
SELECT p.producto_id, p.nombre, p.stock, COALESCE(i.stock_minimo, 0), p.costo,
       p.preciounitario, p.habilitado, p.valor_inventario, now()
FROM productos.producto p
LEFT JOIN inventario.inventario_producto i ON i.producto_id = p.producto_id;

ALTER TABLE inventario.inventario_producto ALTER COLUMN nombre SET NOT NULL;
ALTER TABLE inventario.inventario_producto DROP CONSTRAINT IF EXISTS fk_inventario_producto;
ALTER TABLE inventario.movimiento_inventario DROP CONSTRAINT IF EXISTS fk_movimiento_producto;
ALTER TABLE inventario.kardex_inventario DROP CONSTRAINT IF EXISTS fk_kardex_producto;

-- Verificación esperada antes del despliegue del nuevo inventario-service.
SELECT count(*) AS productos_sin_inventario
FROM productos.producto p
LEFT JOIN inventario.inventario_producto i ON i.producto_id = p.producto_id
WHERE i.producto_id IS NULL;
