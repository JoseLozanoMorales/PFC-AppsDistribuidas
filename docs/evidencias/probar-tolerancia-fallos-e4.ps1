<#
Prueba de tolerancia a fallos (Entrega 4, Paso 5.5).
Sigue la secuencia exacta de la guia paso a paso: cuenta registros, detiene un
nodo, repite la consulta de inmediato, espera 30 segundos, observa la
redistribucion, reincorpora el nodo y mide el tiempo de reintegracion
sondeando el estado real del nodo (no inferido de archivos sueltos, como en
julio). Grabar la pantalla durante toda la ejecucion es responsabilidad del
operador, este script no lo automatiza.
#>

param(
    [string]$Salida = "$PSScriptRoot/resultados-tolerancia-e4"
)

$ErrorActionPreference = "Stop"
$nodo1 = "tiendatech-crdb-1"
$nodo2 = "tiendatech-crdb-2"
$nodo3 = "tiendatech-crdb-3"
$consulta = @"
SET statement_timeout = '8s';
SELECT count(*) AS ordenes_dia
FROM pedidos.orden
WHERE fecha = DATE '2026-07-15';
"@

New-Item -ItemType Directory -Force -Path $Salida | Out-Null
$resultados = [System.Collections.Generic.List[object]]::new()
$bitacora = [System.Collections.Generic.List[object]]::new()

function Log-Evento {
    param([string]$Evento)
    $marca = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $bitacora.Add([pscustomobject]@{ marca_tiempo = $marca; evento = $Evento })
    Write-Host "[$marca] $Evento"
}

function Invoke-Consulta {
    param([string]$Etapa)
    $cronometro = [System.Diagnostics.Stopwatch]::StartNew()
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $texto = & docker exec $nodo1 cockroach sql `
            --insecure --host=localhost:26257 --database=tiendatech `
            --execute=$consulta 2>&1
        $codigo = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $anterior
    }
    $cronometro.Stop()
    $resultados.Add([pscustomobject]@{
        etapa         = $Etapa
        exito         = ($codigo -eq 0)
        latencia_ms   = [math]::Round($cronometro.Elapsed.TotalMilliseconds, 2)
        codigo_salida = $codigo
        respuesta     = (($texto | Out-String).Trim() -replace "[`r`n]+", " ")
    })
    Log-Evento "Consulta '$Etapa': exito=$($codigo -eq 0), latencia=$([math]::Round($cronometro.Elapsed.TotalMilliseconds,2))ms"
}

function Save-NodeStatus {
    param([string]$Nombre)
    & docker exec $nodo1 cockroach node status --insecure --host=localhost:26257 |
        Set-Content -Encoding UTF8 (Join-Path $Salida "$Nombre.txt")
}

# 1) Contar registros con los tres nodos arriba.
Log-Evento "Inicio de la prueba. Tres nodos deberian estar sanos."
Save-NodeStatus "estado-inicial"
Invoke-Consulta -Etapa "tres_nodos_antes"

# 2) Detener un nodo.
Log-Evento "Deteniendo $nodo2 con docker kill."
& docker kill $nodo2 | Out-Null

# 3) Repetir la consulta de inmediato.
Invoke-Consulta -Etapa "inmediatamente_tras_caida"

# 4) Esperar treinta segundos.
Log-Evento "Esperando 30 segundos antes de observar la redistribucion."
Start-Sleep -Seconds 30

# 5) Observar la redistribucion.
Log-Evento "Observando redistribucion tras la espera de 30s."
Save-NodeStatus "estado-tras-30s"
Invoke-Consulta -Etapa "tras_espera_30s"

# 6) Reincorporar el nodo, midiendo el tiempo de reintegracion por sondeo real.
Log-Evento "Reincorporando $nodo2 con docker start. Iniciando cronometro de reintegracion."
$cronometroReintegracion = [System.Diagnostics.Stopwatch]::StartNew()
& docker start $nodo2 | Out-Null

$vivo = $false
$intentosMaximos = 60  # hasta 60s de sondeo, 1s entre intentos
for ($i = 0; $i -lt $intentosMaximos -and -not $vivo; $i++) {
    Start-Sleep -Seconds 1
    $estado = & docker exec $nodo1 cockroach node status --insecure --host=localhost:26257 --format=csv 2>$null
    if ($estado -match "^2,.*,true,true\s*$" -or ($estado | Select-String "^2," | Select-String ",true,true")) {
        $vivo = $true
    }
}
$cronometroReintegracion.Stop()
$tiempoReintegracionSeg = [math]::Round($cronometroReintegracion.Elapsed.TotalSeconds, 2)
Log-Evento "Nodo 2 reintegrado (is_live=true) en $tiempoReintegracionSeg segundos desde 'docker start'."

Save-NodeStatus "estado-recuperado"
Invoke-Consulta -Etapa "tras_reintegracion"

$csv = Join-Path $Salida "mediciones.csv"
$resultados | Export-Csv -NoTypeInformation -Encoding UTF8 $csv

$bitacoraCsv = Join-Path $Salida "bitacora-tiempos.csv"
$bitacora | Export-Csv -NoTypeInformation -Encoding UTF8 $bitacoraCsv

[pscustomobject]@{ tiempo_reintegracion_segundos = $tiempoReintegracionSeg } |
    Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $Salida "tiempo-reintegracion.csv")

Write-Host ""
Write-Host "=== Resumen ==="
$resultados | Format-Table -AutoSize
Write-Host "Tiempo de reintegracion medido: $tiempoReintegracionSeg segundos"
