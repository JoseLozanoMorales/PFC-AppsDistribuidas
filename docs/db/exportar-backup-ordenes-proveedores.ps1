param(
    [Parameter(Mandatory = $true)] [string]$Backup,
    [string]$Salida = "$PSScriptRoot/../../tmp/import-ordenes-proveedores.sql",
    [string]$Database = "tiendatech",
    [string]$PgRestore = "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $Backup)) { throw "No existe el backup: $Backup" }
$temporal = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../../tmp/tiendatech-ordenes-proveedores-export.sql"))
$orden = @("ordenes_proveedores.proveedor", "ordenes_proveedores.orden_compra", "ordenes_proveedores.detalle_orden_compra")

try {
    & $PgRestore --data-only --no-owner --no-privileges --file=$temporal $Backup
    if ($LASTEXITCODE -ne 0) { throw "pg_restore termino con codigo $LASTEXITCODE" }
    $bloques = @{}; $actual = $null
    foreach ($linea in [IO.File]::ReadLines($temporal)) {
        if (-not $actual -and $linea -match '^COPY (ordenes_proveedores\.[^ ]+) \((.+)\) FROM stdin;$') {
            if ($matches[1] -notin $orden) { continue }
            $actual = $matches[1]
            $bloques[$actual] = [pscustomobject]@{ Columnas=$matches[2]; Filas=[Collections.Generic.List[string]]::new() }
            continue
        }
        if (-not $actual) { continue }
        if ($linea -eq '\.') { $actual = $null; continue }
        $bloques[$actual].Filas.Add($linea)
    }
    $writer = [IO.StreamWriter]::new($Salida, $false, [Text.UTF8Encoding]::new($false))
    try {
        $writer.WriteLine("USE $Database;"); $writer.WriteLine()
        foreach ($tabla in $orden) {
            if (-not $bloques[$tabla] -or $bloques[$tabla].Filas.Count -eq 0) { continue }
            $b=$bloques[$tabla]; $writer.WriteLine("COPY $tabla ($($b.Columnas)) FROM STDIN;")
            foreach($fila in $b.Filas){$writer.WriteLine($fila)}
            $writer.WriteLine('\.'); $writer.WriteLine()
        }
        $writer.WriteLine("SELECT setval('ordenes_proveedores.proveedor_proveedor_id_seq', coalesce((SELECT max(proveedor_id) FROM ordenes_proveedores.proveedor),1));")
        $writer.WriteLine("SELECT setval('ordenes_proveedores.orden_compra_orden_compra_id_seq', coalesce((SELECT max(orden_compra_id) FROM ordenes_proveedores.orden_compra),1));")
        $writer.WriteLine("SELECT setval('ordenes_proveedores.detalle_orden_compra_detalle_id_seq', coalesce((SELECT max(detalle_id) FROM ordenes_proveedores.detalle_orden_compra),1));")
        $writer.WriteLine("SELECT 'proveedores' AS entidad, count(*) AS filas FROM ordenes_proveedores.proveedor UNION ALL SELECT 'ordenes', count(*) FROM ordenes_proveedores.orden_compra UNION ALL SELECT 'detalles', count(*) FROM ordenes_proveedores.detalle_orden_compra;")
    } finally { $writer.Dispose() }
} finally { if(Test-Path -LiteralPath $temporal){Remove-Item -LiteralPath $temporal -Force} }
Write-Output "Importacion de ordenes a proveedores generada en: $Salida"
