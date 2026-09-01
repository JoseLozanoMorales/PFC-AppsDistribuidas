<#
Levanta el nodo unico de CockroachDB para el Paso 5.6 (independiente del
cluster de 3 nodos: red/volumen propios, sin puertos publicados al host) y lo
carga con el mismo dataset determinista que ya tiene el cluster (docs/db/
schema.sql, product-management-reference.sql, seeds.sql y
seed-catalogo-sintetico.sql), para que la comparacion de planes de ejecucion
sea sobre volumenes identicos: 10 000 usuarios, 600 000 ordenes, 600 000
detalles, 154 productos.

product-management-reference.sql va antes que seed-catalogo-sintetico.sql
porque este ultimo depende de productos.categoria_producto ya poblada (segun
documenta ADR-012); sin ese paso, seed-catalogo-sintetico.sql no falla, pero
inserta cero filas (join contra una tabla de categorias vacia).

Ejecutar desde la raiz del repositorio:
    .\docs\evidencias\preparar-nodo-unico-e4.ps1

Si una corrida anterior fallo a medio camino (por ejemplo por el error de
CockroachDB "remote wall time is too far ahead" en Docker Desktop/WSL2),
limpiar antes de reintentar:
    docker compose -f docs\evidencias\docker-compose.nodo-unico-e4.yml down -v
#>

$ErrorActionPreference = "Stop"
$contenedor = "tiendatech-crdb-nodo-unico"

Write-Host "Levantando el nodo unico (docker-compose.nodo-unico-e4.yml)..."
docker compose -f docs/evidencias/docker-compose.nodo-unico-e4.yml up -d --wait

Write-Host ""
Write-Host "Nodo unico saludable. Cargando docs/db/schema.sql..."
Get-Content docs/db/schema.sql | docker exec -i $contenedor cockroach sql --insecure --host=localhost:26257

Write-Host ""
Write-Host "Cargando docs/db/product-management-reference.sql (categorias, marcas, gamas, iva)..."
Get-Content docs/db/product-management-reference.sql | docker exec -i $contenedor cockroach sql --insecure --host=localhost:26257

Write-Host ""
Write-Host "Cargando docs/db/seeds.sql (10000 usuarios, 600000 ordenes, 600000 detalles)..."
Write-Host "Esto puede tardar varios minutos: el nodo unico corre con --cache=64MiB, igual que cada nodo del cluster."
Get-Content docs/db/seeds.sql | docker exec -i $contenedor cockroach sql --insecure --host=localhost:26257

Write-Host ""
Write-Host "Cargando docs/db/seed-catalogo-sintetico.sql (150 productos sinteticos)..."
Get-Content docs/db/seed-catalogo-sintetico.sql | docker exec -i $contenedor cockroach sql --insecure --host=localhost:26257

Write-Host ""
Write-Host "=== Verificando conteos (deben coincidir con los del cluster de 3 nodos) ==="
$verificacion = @"
SELECT 'usuarios' AS entidad, count(*) AS filas FROM usuarios.usuario
UNION ALL SELECT 'ordenes', count(*) FROM pedidos.orden
UNION ALL SELECT 'detalles', count(*) FROM pedidos.detalle_orden
UNION ALL SELECT 'productos', count(*) FROM productos.producto
ORDER BY entidad;
"@
$verificacion | docker exec -i $contenedor cockroach sql --insecure --host=localhost:26257 --database=tiendatech
