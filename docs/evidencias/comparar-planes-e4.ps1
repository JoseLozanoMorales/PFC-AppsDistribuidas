<#
Paso 5.6: ejecuta las mismas 5 consultas de dominio con EXPLAIN ANALYZE contra
el cluster de 3 nodos (docker-compose.db.yml, tiendatech-crdb-1) y contra el
nodo unico (docker-compose.nodo-unico-e4.yml, tiendatech-crdb-nodo-unico), y
arma una tabla comparativa. Requiere que ambos esten arriba y con el mismo
dataset cargado (ver preparar-nodo-unico-e4.ps1 para el nodo unico).

Las 5 consultas no son arbitrarias: las 2 primeras son las "consultas de
referencia" que ya documenta docs/db/schema.sql; la 3 es el patron "lectura
conjunta de encabezado y detalle" que ese mismo archivo declara como patron
dominante; la 4 ejercita la fragmentacion de catalogo del Paso 5.3; la 5 es la
unica que barre las 6 particiones/rangos completos de pedidos.orden, sin
filtro de fecha.

Ejecutar desde la raiz del repositorio:
    .\docs\evidencias\comparar-planes-e4.ps1
#>

param(
    [string]$Salida = "$PSScriptRoot/resultados-planes-e4"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $Salida | Out-Null

$nodoClusterContenedor = "tiendatech-crdb-1"
$nodoUnicoContenedor   = "tiendatech-crdb-nodo-unico"

$consultas = [ordered]@{
    "1_historial_usuario" = @"
EXPLAIN ANALYZE
SELECT fecha, orden_id, estado, total
FROM pedidos.orden
WHERE usuario_id = 1
ORDER BY fecha DESC;
"@
    "2_top10_productos_trimestre" = @"
EXPLAIN ANALYZE
SELECT producto_id, sum(cantidad) AS unidades
FROM pedidos.detalle_orden
WHERE fecha >= DATE '2026-07-01' AND fecha < DATE '2026-10-01'
GROUP BY producto_id
ORDER BY unidades DESC
LIMIT 10;
"@
    "3_encabezado_detalle_dia" = @"
EXPLAIN ANALYZE
SELECT o.orden_id, o.estado, o.total, d.producto_id, d.cantidad, d.subtotal
FROM pedidos.orden o
JOIN pedidos.detalle_orden d ON d.fecha = o.fecha AND d.orden_id = o.orden_id
WHERE o.fecha = DATE '2026-07-15';
"@
    "4_catalogo_categoria" = @"
EXPLAIN ANALYZE
SELECT producto_id, nombre, preciounitario, stock
FROM productos.producto
WHERE categoria_id = 6 AND habilitado = true
ORDER BY producto_id;
"@
    "5_reporte_trimestral" = @"
EXPLAIN ANALYZE
SELECT date_trunc('quarter', fecha) AS trimestre, count(*) AS ordenes, sum(total) AS total_ventas
FROM pedidos.orden
GROUP BY trimestre
ORDER BY trimestre;
"@
}

$resumen = [System.Collections.Generic.List[object]]::new()

function Ejecutar-Explain {
    param(
        [string]$Contenedor,
        [string]$Entorno,
        [string]$NombreConsulta,
        [string]$Sql
    )

    $archivo = Join-Path $Salida "$NombreConsulta`_$Entorno.txt"
    $salidaTexto = & docker exec $Contenedor cockroach sql `
        --insecure --host=localhost:26257 --database=tiendatech `
        --execute=$Sql 2>&1
    $salidaTexto | Set-Content -Encoding UTF8 $archivo

    $textoCompleto = $salidaTexto | Out-String
    $tiempoEjecucion     = if ($textoCompleto -match "execution time:\s*([\d\.]+\s*\S+)")   { $matches[1] } else { $null }
    $tiempoPlanificacion = if ($textoCompleto -match "planning time:\s*([\d\.]+\s*\S+)")    { $matches[1] } else { $null }
    $distribucion        = if ($textoCompleto -match "distribution:\s*(\S+)")               { $matches[1] } else { $null }
    $vectorizado         = if ($textoCompleto -match "vectorized:\s*(\S+)")                 { $matches[1] } else { $null }

    $resumen.Add([pscustomobject]@{
        consulta             = $NombreConsulta
        entorno              = $Entorno
        tiempo_planificacion = $tiempoPlanificacion
        tiempo_ejecucion     = $tiempoEjecucion
        distribucion         = $distribucion
        vectorizado          = $vectorizado
    })

    Write-Host "[$Entorno] $NombreConsulta -> ejecucion=$tiempoEjecucion, distribucion=$distribucion"
}

foreach ($nombre in $consultas.Keys) {
    Ejecutar-Explain -Contenedor $nodoClusterContenedor -Entorno "cluster_3nodos" -NombreConsulta $nombre -Sql $consultas[$nombre]
    Ejecutar-Explain -Contenedor $nodoUnicoContenedor   -Entorno "nodo_unico"     -NombreConsulta $nombre -Sql $consultas[$nombre]
}

$csv = Join-Path $Salida "comparativa-planes.csv"
$resumen | Export-Csv -NoTypeInformation -Encoding UTF8 $csv

Write-Host ""
Write-Host "=== Comparativa ==="
$resumen | Format-Table -AutoSize
Write-Host ""
Write-Host "Planes completos (arbol EXPLAIN ANALYZE) guardados en: $Salida"
Write-Host "Tabla resumen: $csv"
