param(
    [Parameter(Mandatory = $true)]
    [string]$Backup,

    # La salida contiene datos reales (incluidos métodos de pago) y se deja en
    # tmp para evitar incorporarla accidentalmente al repositorio.
    [string]$Salida = "$PSScriptRoot/../../tmp/import-pedidos-ventas.sql",

    [string]$Database = "tiendatech",

    [string]$PgRestore = "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Backup)) {
    throw "No existe el backup: $Backup"
}
if (-not (Test-Path -LiteralPath $PgRestore)) {
    throw "No existe pg_restore: $PgRestore"
}

$temporal = Join-Path $PSScriptRoot "../../tmp/tiendatech-data-export.sql"
$temporal = [System.IO.Path]::GetFullPath($temporal)

# Tablas físicas de las particiones PostgreSQL que convergen en una sola tabla
# lógica de CockroachDB.
$destinos = @{
    # Excepción auditada: la copia de carritos en `pedidos` contiene usuarios
    # huérfanos; la fuente histórica `public` conserva las referencias válidas.
    "public.carrito_de_compra" = "pedidos.carrito_de_compra"
    "public.carrito_detalle" = "pedidos.carrito_detalle"
    "pedidos.detalle_orden" = "pedidos.detalle_orden"
    "pedidos.metodopago" = "pedidos.metodopago"
    "pedidos.orden_historica" = "pedidos.orden"
    "pedidos.orden_reciente" = "pedidos.orden"
    "pedidos.tipo_metodopago" = "pedidos.tipo_metodopago"
    "usuarios.usuario" = "usuarios.usuario"
    "ventas.factura_cuerpo" = "ventas.factura_cuerpo"
    "ventas.factura_encabezado" = "ventas.factura_encabezado"
}

try {
    & $PgRestore --data-only --no-owner --no-privileges --file=$temporal $Backup
    if ($LASTEXITCODE -ne 0) {
        throw "pg_restore terminó con código $LASTEXITCODE"
    }

    $bloques = @{}
    $copiando = $false
    $esUsuarios = $false
    $origenActual = $null

    foreach ($linea in [System.IO.File]::ReadLines($temporal)) {
        if (-not $copiando -and
            $linea -match '^COPY ([^ ]+) \((.+)\) FROM stdin;$') {
            $origen = $matches[1]
            $columnas = $matches[2]
            if (-not $destinos.ContainsKey($origen)) {
                continue
            }

            $origenActual = $origen
            $esUsuarios = $origen -eq "usuarios.usuario"
            if ($esUsuarios) {
                $columnas = "usuario_id, nombre, correo, habilitado"
            }
            $bloques[$origen] = [pscustomobject]@{
                Destino = $destinos[$origen]
                Columnas = $columnas
                Filas = [System.Collections.Generic.List[string]]::new()
            }
            $copiando = $true
            continue
        }

        if (-not $copiando) {
            continue
        }
        if ($linea -eq '\.') {
            $copiando = $false
            $esUsuarios = $false
            $origenActual = $null
            continue
        }

        if ($esUsuarios) {
            $campos = $linea.Split("`t")
            if ($campos.Count -lt 10) {
                throw "Fila inesperada en usuarios.usuario"
            }
            $bloques[$origenActual].Filas.Add(
                ($campos[0], $campos[1], $campos[3], $campos[9] -join "`t"))
        }
        else {
            $bloques[$origenActual].Filas.Add($linea)
        }
    }

    $ordenCarga = @(
        "usuarios.usuario",
        "pedidos.tipo_metodopago",
        "pedidos.metodopago",
        "public.carrito_de_compra",
        "public.carrito_detalle",
        "pedidos.orden_historica",
        "pedidos.orden_reciente",
        "pedidos.detalle_orden",
        "ventas.factura_encabezado",
        "ventas.factura_cuerpo"
    )

    $writer = [System.IO.StreamWriter]::new($Salida, $false,
        [System.Text.UTF8Encoding]::new($false))
    try {
        $writer.WriteLine("-- Generado desde $(Split-Path -Leaf $Backup)")
        $writer.WriteLine("-- Datos canónicos iniciales de Usuarios, Pedidos y Ventas.")
        $writer.WriteLine("USE $Database;")
        $writer.WriteLine()

        foreach ($origen in $ordenCarga) {
            if (-not $bloques.ContainsKey($origen)) {
                continue
            }
            $bloque = $bloques[$origen]
            if ($bloque.Filas.Count -eq 0) {
                continue
            }
            $writer.WriteLine("COPY $($bloque.Destino) ($($bloque.Columnas)) FROM STDIN;")
            foreach ($fila in $bloque.Filas) {
                $writer.WriteLine($fila)
            }
            $writer.WriteLine('\.')
            $writer.WriteLine()
        }

        # Ajustar identidades para que las altas posteriores no reutilicen IDs.
        $writer.WriteLine("SELECT setval('usuarios.usuario_usuario_id_seq', coalesce((SELECT max(usuario_id) FROM usuarios.usuario), 1));")
        $writer.WriteLine("SELECT setval('pedidos.carrito_de_compra_carrito_id_seq', coalesce((SELECT max(carrito_id) FROM pedidos.carrito_de_compra), 1));")
        $writer.WriteLine("SELECT setval('pedidos.metodopago_metodopago_id_seq', coalesce((SELECT max(metodopago_id) FROM pedidos.metodopago), 1));")
        $writer.WriteLine("SELECT setval('pedidos.tipo_metodopago_tipo_id_seq', coalesce((SELECT max(tipo_id) FROM pedidos.tipo_metodopago), 1));")
        $writer.WriteLine("SELECT setval('pedidos.orden_orden_id_seq', coalesce((SELECT max(orden_id) FROM pedidos.orden), 1));")
        $writer.WriteLine("SELECT setval('pedidos.detalle_orden_detalle_id_seq', coalesce((SELECT max(detalle_id) FROM pedidos.detalle_orden), 1));")
        $writer.WriteLine("SELECT setval('ventas.factura_encabezado_factura_id_seq', coalesce((SELECT max(factura_id) FROM ventas.factura_encabezado), 1));")
        $writer.WriteLine()
        $writer.WriteLine("SELECT 'usuarios.usuario' AS tabla, count(*) AS filas FROM usuarios.usuario")
        $writer.WriteLine("UNION ALL SELECT 'pedidos.orden', count(*) FROM pedidos.orden")
        $writer.WriteLine("UNION ALL SELECT 'pedidos.detalle_orden', count(*) FROM pedidos.detalle_orden")
        $writer.WriteLine("UNION ALL SELECT 'ventas.factura_encabezado', count(*) FROM ventas.factura_encabezado")
        $writer.WriteLine("UNION ALL SELECT 'ventas.factura_cuerpo', count(*) FROM ventas.factura_cuerpo;")
    }
    finally {
        $writer.Dispose()
    }
}
finally {
    if (Test-Path -LiteralPath $temporal) {
        Remove-Item -LiteralPath $temporal -Force
    }
}

Write-Output "Importación generada en: $Salida"
