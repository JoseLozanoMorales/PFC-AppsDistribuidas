-- Catálogos mínimos y limpios para gestionar productos en TiendaTech CRDB.
-- No inserta usuarios, productos, inventario, órdenes ni ventas.
USE tiendatech;

UPSERT INTO productos.categoria_producto
    (id_categoria, nombre, habilitado, obligatoria_pc, peso_presupuesto)
VALUES
    (1, 'Almacenamiento', true, true, 0.12),
    (2, 'CPU', true, true, 0.20),
    (3, 'CPU Cooler', true, false, 0.06),
    (4, 'Cubierta', true, true, 0.08),
    (5, 'Fuente de poder', true, true, 0.10),
    (6, 'GPU', true, true, 0.24),
    (7, 'RAM', true, true, 0.08),
    (8, 'Motherboard', true, true, 0.10),
    (9, 'Periféricos', true, false, 0.02),
    (10, 'Accesorios', true, false, 0.00);

UPSERT INTO productos.marca (marca_id, nombre, habilitado) VALUES
    (1, 'Intel', true), (2, 'AMD', true), (3, 'ASUS', true),
    (4, 'MSI', true), (5, 'Gigabyte', true), (6, 'Corsair', true),
    (7, 'Kingston', true), (8, 'Samsung', true), (9, 'Western Digital', true),
    (10, 'Seagate', true), (11, 'Cooler Master', true), (12, 'EVGA', true),
    (13, 'Logitech', true), (14, 'Razer', true), (15, 'Redragon', true),
    (16, 'HyperX', true), (17, 'Crucial', true), (18, 'Noctua', true),
    (19, 'Thermaltake', true), (20, 'Genérica', true);

UPSERT INTO productos.gama (gama_id, tipo_gama, precio_ensamblado, habilitado) VALUES
    (1, 'Entrada', 15.00, true),
    (2, 'Media', 25.00, true),
    (3, 'Alta', 40.00, true),
    (4, 'Entusiasta', 60.00, true);

UPSERT INTO productos.iva (iva_id, porcentaje, habilitado) VALUES
    (1, 15.00, true),
    (2, 0.00, true);
