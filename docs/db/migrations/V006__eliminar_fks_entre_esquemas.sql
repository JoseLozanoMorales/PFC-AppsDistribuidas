-- Los IDs externos se validan mediante las APIs propietarias. Las FK entre
-- esquemas acoplan despliegues y contradicen database/schema-per-service.
ALTER TABLE pedidos.orden DROP CONSTRAINT IF EXISTS fk_orden_usuario;
ALTER TABLE pedidos.carrito_de_compra DROP CONSTRAINT IF EXISTS fk_carrito_usuario;
ALTER TABLE pedidos.metodopago DROP CONSTRAINT IF EXISTS fk_metodopago_usuario;
ALTER TABLE pedidos.solicitud_idempotente
    DROP CONSTRAINT IF EXISTS fk_solicitud_idempotente_usuario;
ALTER TABLE ordenes_proveedores.orden_compra
    DROP CONSTRAINT IF EXISTS fk_orden_compra_usuario;
ALTER TABLE ordenes_proveedores.detalle_orden_compra
    DROP CONSTRAINT IF EXISTS fk_detalle_producto;

-- Debe devolver cero.
SELECT count(*) AS fks_entre_esquemas
FROM information_schema.referential_constraints rc
JOIN information_schema.table_constraints tc
  ON tc.constraint_catalog = rc.constraint_catalog
 AND tc.constraint_schema = rc.constraint_schema
 AND tc.constraint_name = rc.constraint_name
JOIN information_schema.constraint_column_usage ccu
  ON ccu.constraint_catalog = rc.unique_constraint_catalog
 AND ccu.constraint_schema = rc.unique_constraint_schema
 AND ccu.constraint_name = rc.unique_constraint_name
WHERE tc.table_schema <> ccu.table_schema;
