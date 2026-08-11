param(
    [Parameter(Mandatory = $true)]
    [string]$Backup,
    [string]$Salida = "$PSScriptRoot/../../tmp/import-productos.sql",
    [string]$Database = "tiendatech",
    [string]$PgRestore = "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $Backup)) { throw "No existe el backup: $Backup" }
if (-not (Test-Path -LiteralPath $PgRestore)) { throw "No existe pg_restore: $PgRestore" }

$temporal = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../../tmp/tiendatech-productos-export.sql"))
$ordenCarga = @(
    "productos.categoria_producto",
    "productos.marca",
    "productos.gama",
    "productos.iva",
    "productos.producto",
    "productos.galeria_productos_v2"
)

try {
    & $PgRestore --data-only --no-owner --no-privileges --file=$temporal $Backup
    if ($LASTEXITCODE -ne 0) { throw "pg_restore terminó con código $LASTEXITCODE" }

    $bloques = @{}
    $actual = $null
    foreach ($linea in [IO.File]::ReadLines($temporal)) {
        if (-not $actual -and $linea -match '^COPY (productos\.[^ ]+) \((.+)\) FROM stdin;$') {
            $tabla = $matches[1]
            if ($tabla -notin $ordenCarga) { continue }
            $actual = $tabla
            $bloques[$tabla] = [pscustomobject]@{
                Columnas = $matches[2]
                Filas = [Collections.Generic.List[string]]::new()
            }
            continue
        }
        if (-not $actual) { continue }
        if ($linea -eq '\.') { $actual = $null; continue }
        $bloques[$actual].Filas.Add($linea)
    }

    $writer = [IO.StreamWriter]::new($Salida, $false, [Text.UTF8Encoding]::new($false))
    try {
        $writer.WriteLine("-- Generado desde $(Split-Path -Leaf $Backup)")
        $writer.WriteLine("USE $Database;")
        $writer.WriteLine()
        foreach ($tabla in $ordenCarga) {
            if (-not $bloques.ContainsKey($tabla) -or $bloques[$tabla].Filas.Count -eq 0) { continue }
            $bloque = $bloques[$tabla]
            $writer.WriteLine("COPY $tabla ($($bloque.Columnas)) FROM STDIN;")
            foreach ($fila in $bloque.Filas) {
                # pg_restore escapa BYTEA como \\x.... CockroachDB espera \x....
                # dentro de COPY; de lo contrario almacena el hexadecimal como texto.
                if ($tabla -eq "productos.galeria_productos_v2") {
                    $fila = $fila -replace '\\\\x', '\x'
                }
                $writer.WriteLine($fila)
            }
            $writer.WriteLine('\.')
            $writer.WriteLine()
        }
        $writer.WriteLine("SELECT setval('productos.categoria_producto_id_categoria_seq', coalesce((SELECT max(id_categoria) FROM productos.categoria_producto), 1));")
        $writer.WriteLine("SELECT setval('productos.marca_marca_id_seq', coalesce((SELECT max(marca_id) FROM productos.marca), 1));")
        $writer.WriteLine("SELECT setval('productos.gama_gama_id_seq', coalesce((SELECT max(gama_id) FROM productos.gama), 1));")
        $writer.WriteLine("SELECT setval('productos.iva_iva_id_seq', coalesce((SELECT max(iva_id) FROM productos.iva), 1));")
        $writer.WriteLine("SELECT setval('productos.producto_producto_id_seq', coalesce((SELECT max(producto_id) FROM productos.producto), 1));")
        $writer.WriteLine("SELECT setval('productos.galeria_productos_v2_galeria_id_seq', coalesce((SELECT max(galeria_id) FROM productos.galeria_productos_v2), 1));")
        $writer.WriteLine("SELECT 'productos' AS entidad, count(*) AS filas FROM productos.producto")
        $writer.WriteLine("UNION ALL SELECT 'galeria', count(*) FROM productos.galeria_productos_v2;")
    }
    finally { $writer.Dispose() }
}
finally {
    if (Test-Path -LiteralPath $temporal) { Remove-Item -LiteralPath $temporal -Force }
}

Write-Output "Importación de Productos generada en: $Salida"
