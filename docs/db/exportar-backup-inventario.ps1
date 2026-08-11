param(
    [Parameter(Mandatory = $true)] [string]$Backup,
    [string]$Salida = "$PSScriptRoot/../../tmp/import-inventario.sql",
    [string]$Database = "tiendatech",
    [string]$PgRestore = "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $Backup)) { throw "No existe el backup: $Backup" }

$temporal = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../../tmp/tiendatech-inventario-export.sql"))
$orden = @(
    "inventario.tipo_movimiento", "inventario.subtipo_movimiento",
    "inventario.inventario_producto", "inventario.movimiento_inventario",
    "inventario.kardex_inventario"
)

try {
    & $PgRestore --data-only --no-owner --no-privileges --file=$temporal $Backup
    if ($LASTEXITCODE -ne 0) { throw "pg_restore terminó con código $LASTEXITCODE" }
    $bloques = @{}; $actual = $null
    foreach ($linea in [IO.File]::ReadLines($temporal)) {
        if (-not $actual -and $linea -match '^COPY (inventario\.[^ ]+) \((.+)\) FROM stdin;$') {
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
            $writer.WriteLine('\.');$writer.WriteLine()
        }
        $writer.WriteLine("SELECT setval('inventario.tipo_movimiento_tipo_id_seq', coalesce((SELECT max(tipo_id) FROM inventario.tipo_movimiento),1));")
        $writer.WriteLine("SELECT setval('inventario.subtipo_movimiento_subtipo_id_seq', coalesce((SELECT max(subtipo_id) FROM inventario.subtipo_movimiento),1));")
        $writer.WriteLine("SELECT setval('inventario.movimiento_inventario_movimiento_id_seq', coalesce((SELECT max(movimiento_id) FROM inventario.movimiento_inventario),1));")
        $writer.WriteLine("SELECT setval('inventario.kardex_inventario_kardex_id_seq', coalesce((SELECT max(kardex_id) FROM inventario.kardex_inventario),1));")
        $writer.WriteLine("SELECT 'inventario' AS entidad, count(*) AS filas FROM inventario.inventario_producto UNION ALL SELECT 'movimientos', count(*) FROM inventario.movimiento_inventario UNION ALL SELECT 'kardex', count(*) FROM inventario.kardex_inventario;")
    } finally { $writer.Dispose() }
} finally { if(Test-Path -LiteralPath $temporal){Remove-Item -LiteralPath $temporal -Force} }

Write-Output "Importación de Inventario generada en: $Salida"

