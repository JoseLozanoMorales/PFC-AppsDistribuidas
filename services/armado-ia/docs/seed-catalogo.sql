-- Seed de catalogo para probar armado-ia-service
-- ================================================
--
-- productos.producto, categoria_producto, marca, iva y gama estan VACIAS en
-- el catalogo real (verificado en vivo antes de escribir esto). Sin datos,
-- POST /api/armado/analizar no tiene nada que analizar. Este archivo NO es
-- parte de docs/db/schema.sql/seeds.sql (compartidos del equipo) -- es solo
-- para levantar datos de prueba locales.
--
-- Como aplicarlo (perfil e3-crdb, servicio corriendo con
-- docker compose --profile e3-crdb up -d --build armado-ia-service):
--
--   docker exec -i tiendatech-crdb-1 cockroach sql --insecure \
--       --host=localhost:26257 -d tiendatech \
--       < services/armado-ia/docs/seed-catalogo.sql
--
-- AVISO PARA EL EQUIPO DE PRODUCTOS-SERVICE: los nombres de categoria de
-- abajo (columna nombre de categoria_producto) deben coincidir EXACTO
-- (sin distinguir mayusculas, pero si tildes/ortografia) con
-- armado.categorias.*.nombre en services/armado-ia/src/main/resources/
-- application.properties -- ahi es donde armado-ia resuelve categoriaId por
-- nombre porque GET /api/categorias todavia no expone obligatoria_pc ni
-- peso_presupuesto. Si cambian estos nombres en la base real, hay que
-- actualizar ese archivo de properties tambien (o mejor: extender
-- GET /api/categorias para devolver esas 2 columnas y que armado-ia deje de
-- necesitar este espejo).

-- ---------------------------------------------------------------------
-- IVA
-- ---------------------------------------------------------------------
INSERT INTO productos.iva (iva_id, porcentaje, habilitado)
VALUES (1, 15.00, true)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- Marcas
-- ---------------------------------------------------------------------
INSERT INTO productos.marca (nombre, habilitado) VALUES
    ('Intel', true), ('AMD', true), ('NVIDIA', true), ('ASUS', true),
    ('Gigabyte', true), ('Kingston', true), ('Corsair', true), ('Seagate', true),
    ('Samsung', true), ('EVGA', true), ('Cooler Master', true), ('NZXT', true),
    ('Logitech', true), ('Redragon', true)
ON CONFLICT (nombre) DO NOTHING;

-- ---------------------------------------------------------------------
-- Gama (referencia, no la usa el algoritmo de armado-ia directamente)
-- ---------------------------------------------------------------------
INSERT INTO productos.gama (tipo_gama, precio_ensamblado, habilitado) VALUES
    ('Baja', 500.00, true),
    ('Media', 1000.00, true),
    ('Alta', 2000.00, true)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- Categorias: IDs EXPLICITOS para que coincidan con los que ya hardcodea
-- Apps/web/frontend/webapp/src/views/BuilderView.vue (cpu=2, mobo=8, ram=7,
-- storage=1, gpu=6, psu=5, case=4, cooling=3, periferico=9).
-- obligatoria_pc / peso_presupuesto reflejan lo mismo que
-- armado.categorias.* en application.properties de armado-ia.
-- ---------------------------------------------------------------------
INSERT INTO productos.categoria_producto
    (id_categoria, nombre, habilitado, obligatoria_pc, peso_presupuesto)
VALUES
    (1, 'Almacenamiento',   true, true,  0.08),
    (2, 'Procesador',       true, true,  0.30),
    (3, 'Refrigeracion',    true, false, 0.01),
    (4, 'Gabinete',         true, true,  0.03),
    (5, 'Fuente de poder',  true, true,  0.05),
    (6, 'Tarjeta grafica',  true, false, 0.30),
    (7, 'Memoria RAM',      true, true,  0.10),
    (8, 'Motherboard',      true, true,  0.12),
    (9, 'Perifericos',      true, false, 0.01)
ON CONFLICT (id_categoria) DO NOTHING;

SELECT setval(
    'productos.categoria_producto_id_categoria_seq',
    coalesce((SELECT max(id_categoria) FROM productos.categoria_producto), 0)
);

-- ---------------------------------------------------------------------
-- Productos. Atributos cubren exactamente lo que BottleneckCalculator /
-- AdvertenciasTecnicas / RecomendadorService leen en armado-ia:
--   CPU:      nucleos, hilos, socket, tdp, frecuencia_turbo_ghz
--   GPU:      nucleos, vram_gb, tdp
--   RAM:      capacidad_gb, velocidad_mhz, tipo
--   Storage:  tipo (HDD/SSD/NVME), capacidad_gb
--   Mobo:     socket (debe coincidir con el socket de alguna CPU de arriba)
--   PSU:      potencia_watts
-- Deliberadamente incluye 2 familias de socket (LGA1700 y AM5) para poder
-- probar la compatibilidad CPU<->Motherboard, tanto la advertencia como la
-- restriccion dura del recomendador.
-- ---------------------------------------------------------------------

-- CPU (categoria_id = 2)
INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Intel Core i5-13400F', 220.00, 15, m.marca_id, 1, 155.00, 2,
       '{"nucleos":10,"hilos":16,"socket":"LGA1700","tdp":65,"frecuencia_turbo_ghz":4.6}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Intel'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Intel Core i9-13900K', 580.00, 8, m.marca_id, 1, 420.00, 2,
       '{"nucleos":24,"hilos":32,"socket":"LGA1700","tdp":125,"frecuencia_turbo_ghz":5.8}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Intel'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'AMD Ryzen 5 7600', 230.00, 12, m.marca_id, 1, 165.00, 2,
       '{"nucleos":6,"hilos":12,"socket":"AM5","tdp":65,"frecuencia_turbo_ghz":5.1}'::JSONB
FROM productos.marca m WHERE m.nombre = 'AMD'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'AMD Ryzen 9 7950X', 550.00, 6, m.marca_id, 1, 400.00, 2,
       '{"nucleos":16,"hilos":32,"socket":"AM5","tdp":170,"frecuencia_turbo_ghz":5.7}'::JSONB
FROM productos.marca m WHERE m.nombre = 'AMD'
ON CONFLICT (nombre) DO NOTHING;

-- Motherboard (categoria_id = 8)
INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'ASUS Prime B760M-A', 130.00, 10, m.marca_id, 1, 95.00, 8,
       '{"socket":"LGA1700","chipset":"B760","memoria_max_gb":128,"form_factor":"mATX"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'ASUS'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'ASUS ROG Strix Z790-E', 470.00, 4, m.marca_id, 1, 350.00, 8,
       '{"socket":"LGA1700","chipset":"Z790","memoria_max_gb":128,"form_factor":"ATX"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'ASUS'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'ASUS TUF Gaming B650-Plus', 170.00, 9, m.marca_id, 1, 125.00, 8,
       '{"socket":"AM5","chipset":"B650","memoria_max_gb":128,"form_factor":"ATX"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'ASUS'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Gigabyte X670E Aorus Elite', 300.00, 5, m.marca_id, 1, 220.00, 8,
       '{"socket":"AM5","chipset":"X670E","memoria_max_gb":128,"form_factor":"ATX"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Gigabyte'
ON CONFLICT (nombre) DO NOTHING;

-- Memoria RAM (categoria_id = 7)
INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Kingston Fury Beast 16GB DDR4 3200', 45.00, 25, m.marca_id, 1, 32.00, 7,
       '{"capacidad_gb":16,"velocidad_mhz":3200,"tipo":"DDR4"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Kingston'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Corsair Vengeance 32GB DDR5 6000', 110.00, 14, m.marca_id, 1, 80.00, 7,
       '{"capacidad_gb":32,"velocidad_mhz":6000,"tipo":"DDR5"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Corsair'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Corsair Vengeance 64GB DDR5 5600', 220.00, 7, m.marca_id, 1, 165.00, 7,
       '{"capacidad_gb":64,"velocidad_mhz":5600,"tipo":"DDR5"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Corsair'
ON CONFLICT (nombre) DO NOTHING;

-- Almacenamiento (categoria_id = 1)
INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Seagate Barracuda 1TB HDD', 40.00, 20, m.marca_id, 1, 28.00, 1,
       '{"tipo":"HDD","capacidad_gb":1000,"velocidad_lectura_mbps":190}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Seagate'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Kingston A400 480GB SSD', 35.00, 18, m.marca_id, 1, 24.00, 1,
       '{"tipo":"SSD","capacidad_gb":480,"velocidad_lectura_mbps":500}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Kingston'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Samsung 970 EVO Plus 1TB NVMe', 75.00, 16, m.marca_id, 1, 55.00, 1,
       '{"tipo":"NVME","capacidad_gb":1000,"velocidad_lectura_mbps":3500}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Samsung'
ON CONFLICT (nombre) DO NOTHING;

-- Tarjeta grafica (categoria_id = 6)
INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'AMD Radeon RX 7600', 270.00, 9, m.marca_id, 1, 195.00, 6,
       '{"nucleos":2048,"vram_gb":8,"tdp":165}'::JSONB
FROM productos.marca m WHERE m.nombre = 'AMD'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'NVIDIA RTX 4060', 320.00, 11, m.marca_id, 1, 235.00, 6,
       '{"nucleos":3072,"vram_gb":8,"tdp":115}'::JSONB
FROM productos.marca m WHERE m.nombre = 'NVIDIA'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'NVIDIA RTX 4070 Ti', 780.00, 4, m.marca_id, 1, 590.00, 6,
       '{"nucleos":7680,"vram_gb":12,"tdp":285}'::JSONB
FROM productos.marca m WHERE m.nombre = 'NVIDIA'
ON CONFLICT (nombre) DO NOTHING;

-- Fuente de poder (categoria_id = 5)
INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Corsair CV550 550W', 55.00, 20, m.marca_id, 1, 38.00, 5,
       '{"potencia_watts":550,"certificacion":"80+ Bronze"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Corsair'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'EVGA SuperNOVA 750W', 95.00, 13, m.marca_id, 1, 68.00, 5,
       '{"potencia_watts":750,"certificacion":"80+ Gold"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'EVGA'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Corsair RM1000x 1000W', 170.00, 6, m.marca_id, 1, 128.00, 5,
       '{"potencia_watts":1000,"certificacion":"80+ Gold"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Corsair'
ON CONFLICT (nombre) DO NOTHING;

-- Gabinete (categoria_id = 4)
INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Cooler Master MasterBox Q300L', 50.00, 15, m.marca_id, 1, 34.00, 4,
       '{"form_factor_compatible":"mATX"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Cooler Master'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'NZXT H510', 80.00, 11, m.marca_id, 1, 58.00, 4,
       '{"form_factor_compatible":"ATX"}'::JSONB
FROM productos.marca m WHERE m.nombre = 'NZXT'
ON CONFLICT (nombre) DO NOTHING;

-- Refrigeracion (categoria_id = 3)
INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Cooler Master Hyper 212', 35.00, 22, m.marca_id, 1, 24.00, 3,
       '{"tipo":"aire","tdp_soportado":150}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Cooler Master'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Corsair iCUE H100i 240mm', 110.00, 9, m.marca_id, 1, 80.00, 3,
       '{"tipo":"liquido","tdp_soportado":250}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Corsair'
ON CONFLICT (nombre) DO NOTHING;

-- Perifericos (categoria_id = 9) -- no participan del calculo de bottleneck
INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Logitech MK270 Combo', 25.00, 30, m.marca_id, 1, 16.00, 9, '{}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Logitech'
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO productos.producto
    (nombre, preciounitario, stock, marca_id, iva_id, costo, categoria_id, atributos)
SELECT 'Redragon K552 Teclado Mecanico', 40.00, 17, m.marca_id, 1, 27.00, 9, '{}'::JSONB
FROM productos.marca m WHERE m.nombre = 'Redragon'
ON CONFLICT (nombre) DO NOTHING;

-- ---------------------------------------------------------------------
-- Verificacion rapida
-- ---------------------------------------------------------------------
SELECT c.nombre AS categoria, count(*) AS productos
FROM productos.producto p
JOIN productos.categoria_producto c ON c.id_categoria = p.categoria_id
GROUP BY c.nombre
ORDER BY c.nombre;
