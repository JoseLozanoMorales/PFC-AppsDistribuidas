-- Catálogos mínimos para direcciones y métodos de pago de la app móvil.
-- Compatible con CockroachDB. Puede ejecutarse varias veces sin duplicar
-- combinaciones existentes de provincia/ciudad ni tipos de pago.

BEGIN;

WITH datos(nombre) AS (
    VALUES
        ('Azuay'), ('Bolívar'), ('Cañar'), ('Carchi'), ('Chimborazo'),
        ('Cotopaxi'), ('El Oro'), ('Esmeraldas'), ('Galápagos'), ('Guayas'),
        ('Imbabura'), ('Loja'), ('Los Ríos'), ('Manabí'),
        ('Morona Santiago'), ('Napo'), ('Orellana'), ('Pastaza'),
        ('Pichincha'), ('Santa Elena'), ('Santo Domingo de los Tsáchilas'),
        ('Sucumbíos'), ('Tungurahua'), ('Zamora Chinchipe')
)
INSERT INTO usuarios.provincia (nombre, habilitado)
SELECT d.nombre, true
FROM datos d
WHERE NOT EXISTS (
    SELECT 1
    FROM usuarios.provincia p
    WHERE lower(trim(p.nombre)) = lower(trim(d.nombre))
);

WITH datos(nombre) AS (
    VALUES
        ('Azuay'), ('Bolívar'), ('Cañar'), ('Carchi'), ('Chimborazo'),
        ('Cotopaxi'), ('El Oro'), ('Esmeraldas'), ('Galápagos'), ('Guayas'),
        ('Imbabura'), ('Loja'), ('Los Ríos'), ('Manabí'),
        ('Morona Santiago'), ('Napo'), ('Orellana'), ('Pastaza'),
        ('Pichincha'), ('Santa Elena'), ('Santo Domingo de los Tsáchilas'),
        ('Sucumbíos'), ('Tungurahua'), ('Zamora Chinchipe')
)
UPDATE usuarios.provincia p
SET habilitado = true
FROM datos d
WHERE lower(trim(p.nombre)) = lower(trim(d.nombre));

WITH datos(provincia, ciudad) AS (
    VALUES
        ('Azuay', 'Cuenca'),
        ('Azuay', 'Gualaceo'),
        ('Azuay', 'Paute'),
        ('Bolívar', 'Guaranda'),
        ('Bolívar', 'San Miguel'),
        ('Cañar', 'Azogues'),
        ('Cañar', 'La Troncal'),
        ('Carchi', 'Tulcán'),
        ('Carchi', 'San Gabriel'),
        ('Chimborazo', 'Riobamba'),
        ('Chimborazo', 'Alausí'),
        ('Cotopaxi', 'Latacunga'),
        ('Cotopaxi', 'La Maná'),
        ('El Oro', 'Machala'),
        ('El Oro', 'Huaquillas'),
        ('El Oro', 'Pasaje'),
        ('Esmeraldas', 'Esmeraldas'),
        ('Esmeraldas', 'Atacames'),
        ('Galápagos', 'Puerto Baquerizo Moreno'),
        ('Galápagos', 'Puerto Ayora'),
        ('Guayas', 'Guayaquil'),
        ('Guayas', 'Durán'),
        ('Guayas', 'Milagro'),
        ('Guayas', 'Daule'),
        ('Imbabura', 'Ibarra'),
        ('Imbabura', 'Otavalo'),
        ('Loja', 'Loja'),
        ('Loja', 'Catamayo'),
        ('Los Ríos', 'Babahoyo'),
        ('Los Ríos', 'Quevedo'),
        ('Los Ríos', 'Ventanas'),
        ('Manabí', 'Portoviejo'),
        ('Manabí', 'Manta'),
        ('Manabí', 'Chone'),
        ('Morona Santiago', 'Macas'),
        ('Morona Santiago', 'Sucúa'),
        ('Napo', 'Tena'),
        ('Orellana', 'Puerto Francisco de Orellana'),
        ('Pastaza', 'Puyo'),
        ('Pichincha', 'Quito'),
        ('Pichincha', 'Cayambe'),
        ('Pichincha', 'Sangolquí'),
        ('Santa Elena', 'Santa Elena'),
        ('Santa Elena', 'La Libertad'),
        ('Santa Elena', 'Salinas'),
        ('Santo Domingo de los Tsáchilas', 'Santo Domingo'),
        ('Sucumbíos', 'Nueva Loja'),
        ('Tungurahua', 'Ambato'),
        ('Tungurahua', 'Baños de Agua Santa'),
        ('Zamora Chinchipe', 'Zamora')
), provincias AS (
    SELECT min(p.provincia_id) AS provincia_id, lower(trim(p.nombre)) AS clave
    FROM usuarios.provincia p
    GROUP BY lower(trim(p.nombre))
)
INSERT INTO usuarios.ciudad (nombre, provincia_id, habilitado)
SELECT d.ciudad, p.provincia_id, true
FROM datos d
JOIN provincias p ON p.clave = lower(trim(d.provincia))
WHERE NOT EXISTS (
    SELECT 1
    FROM usuarios.ciudad c
    WHERE c.provincia_id = p.provincia_id
      AND lower(trim(c.nombre)) = lower(trim(d.ciudad))
);

WITH datos(provincia, ciudad) AS (
    VALUES
        ('Azuay', 'Cuenca'), ('Azuay', 'Gualaceo'), ('Azuay', 'Paute'),
        ('Bolívar', 'Guaranda'), ('Bolívar', 'San Miguel'),
        ('Cañar', 'Azogues'), ('Cañar', 'La Troncal'),
        ('Carchi', 'Tulcán'), ('Carchi', 'San Gabriel'),
        ('Chimborazo', 'Riobamba'), ('Chimborazo', 'Alausí'),
        ('Cotopaxi', 'Latacunga'), ('Cotopaxi', 'La Maná'),
        ('El Oro', 'Machala'), ('El Oro', 'Huaquillas'), ('El Oro', 'Pasaje'),
        ('Esmeraldas', 'Esmeraldas'), ('Esmeraldas', 'Atacames'),
        ('Galápagos', 'Puerto Baquerizo Moreno'), ('Galápagos', 'Puerto Ayora'),
        ('Guayas', 'Guayaquil'), ('Guayas', 'Durán'), ('Guayas', 'Milagro'), ('Guayas', 'Daule'),
        ('Imbabura', 'Ibarra'), ('Imbabura', 'Otavalo'),
        ('Loja', 'Loja'), ('Loja', 'Catamayo'),
        ('Los Ríos', 'Babahoyo'), ('Los Ríos', 'Quevedo'), ('Los Ríos', 'Ventanas'),
        ('Manabí', 'Portoviejo'), ('Manabí', 'Manta'), ('Manabí', 'Chone'),
        ('Morona Santiago', 'Macas'), ('Morona Santiago', 'Sucúa'),
        ('Napo', 'Tena'), ('Orellana', 'Puerto Francisco de Orellana'),
        ('Pastaza', 'Puyo'),
        ('Pichincha', 'Quito'), ('Pichincha', 'Cayambe'), ('Pichincha', 'Sangolquí'),
        ('Santa Elena', 'Santa Elena'), ('Santa Elena', 'La Libertad'), ('Santa Elena', 'Salinas'),
        ('Santo Domingo de los Tsáchilas', 'Santo Domingo'),
        ('Sucumbíos', 'Nueva Loja'),
        ('Tungurahua', 'Ambato'), ('Tungurahua', 'Baños de Agua Santa'),
        ('Zamora Chinchipe', 'Zamora')
), provincias AS (
    SELECT min(p.provincia_id) AS provincia_id, lower(trim(p.nombre)) AS clave
    FROM usuarios.provincia p
    GROUP BY lower(trim(p.nombre))
)
UPDATE usuarios.ciudad c
SET habilitado = true
FROM datos d
JOIN provincias p ON p.clave = lower(trim(d.provincia))
WHERE c.provincia_id = p.provincia_id
  AND lower(trim(c.nombre)) = lower(trim(d.ciudad));

INSERT INTO pedidos.tipo_metodopago (nombre, habilitado)
VALUES ('Débito', true), ('Crédito', true)
ON CONFLICT (nombre) DO UPDATE SET habilitado = true;

COMMIT;

-- Verificación posterior.
SELECT count(*) AS provincias_habilitadas
FROM usuarios.provincia
WHERE habilitado;

SELECT count(*) AS ciudades_habilitadas
FROM usuarios.ciudad
WHERE habilitado;

SELECT tipo_id, nombre, habilitado
FROM pedidos.tipo_metodopago
ORDER BY tipo_id;
