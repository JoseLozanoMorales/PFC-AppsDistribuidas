-- Catalogo minimo de inventario.tipo_movimiento / inventario.subtipo_movimiento.
-- schema.sql crea estas tablas vacias; nunca se sembraron en ningun script.
-- ordenes-proveedores-service e ventas-service asumen ids fijos por contrato:
--   subtipo_id = 1 -> "COMPRA"  (InventarioClient.SUBTIPO_COMPRA en ordenes-proveedores-service)
--   subtipo_id = 4 -> "VENTA"   (InventarioClient.SUBTIPO_VENTA en ventas-service)
-- Los ids 2 y 3 son de relleno para no dejar huecos en la secuencia.
USE tiendatech;

UPSERT INTO inventario.tipo_movimiento (tipo_id, nombre) VALUES
    (1, 'ENTRADA'),
    (2, 'SALIDA'),
    (3, 'AJUSTE');

UPSERT INTO inventario.subtipo_movimiento (subtipo_id, nombre, tipo_id) VALUES
    (1, 'COMPRA', 1),
    (2, 'AJUSTE', 3),
    (3, 'DEVOLUCION_PROVEEDOR', 2),
    (4, 'VENTA', 2);
