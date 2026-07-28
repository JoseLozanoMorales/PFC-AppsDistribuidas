param(
    [int]$Repeticiones = 5,
    [string]$Salida = "$PSScriptRoot/resultados-tolerancia"
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

function Invoke-Consulta {
    param(
        [string]$Etapa,
        [int]$Repeticion
    )

    $cronometro = [System.Diagnostics.Stopwatch]::StartNew()
    $preferenciaAnterior = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $texto = & docker exec $nodo1 cockroach sql `
            --insecure `
            --host=localhost:26257 `
            --database=tiendatech `
            --execute=$consulta 2>&1
        $codigo = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $preferenciaAnterior
    }
    $cronometro.Stop()

    $resultados.Add([pscustomobject]@{
        etapa = $Etapa
        repeticion = $Repeticion
        exito = ($codigo -eq 0)
        latencia_ms = [math]::Round($cronometro.Elapsed.TotalMilliseconds, 2)
        codigo_salida = $codigo
        respuesta = (($texto | Out-String).Trim() -replace "[`r`n]+", " ")
    })
}

function Save-NodeStatus {
    param([string]$Nombre)
    & docker exec $nodo1 cockroach node status `
        --insecure --host=localhost:26257 |
        Set-Content -Encoding UTF8 (Join-Path $Salida "$Nombre.txt")
}

try {
    & docker start $nodo1 $nodo2 $nodo3 | Out-Null
    Start-Sleep -Seconds 8

    Save-NodeStatus "estado-inicial"
    1..$Repeticiones | ForEach-Object {
        Invoke-Consulta -Etapa "tres_nodos" -Repeticion $_
    }

    & docker kill $nodo2 | Out-Null
    Start-Sleep -Seconds 8
    Save-NodeStatus "estado-un-nodo-caido"
    1..$Repeticiones | ForEach-Object {
        Invoke-Consulta -Etapa "un_nodo_caido" -Repeticion $_
    }

    & docker start $nodo2 | Out-Null
    Start-Sleep -Seconds 12
    Save-NodeStatus "estado-recuperado"
    1..$Repeticiones | ForEach-Object {
        Invoke-Consulta -Etapa "recuperado" -Repeticion $_
    }

    & docker kill $nodo2 $nodo3 | Out-Null
    Start-Sleep -Seconds 8
    Invoke-Consulta -Etapa "dos_nodos_caidos" -Repeticion 1
}
finally {
    & docker start $nodo2 $nodo3 | Out-Null
    Start-Sleep -Seconds 12
    Save-NodeStatus "estado-final"
}

$csv = Join-Path $Salida "mediciones.csv"
$resultados | Export-Csv -NoTypeInformation -Encoding UTF8 $csv

$resumen = $resultados |
    Group-Object etapa |
    ForEach-Object {
        $exitos = @($_.Group | Where-Object exito)
        [pscustomobject]@{
            etapa = $_.Name
            intentos = $_.Count
            exitos = $exitos.Count
            latencia_promedio_ms = if ($exitos.Count) {
                [math]::Round(($exitos | Measure-Object latencia_ms -Average).Average, 2)
            } else {
                $null
            }
            latencia_maxima_ms = if ($exitos.Count) {
                [math]::Round(($exitos | Measure-Object latencia_ms -Maximum).Maximum, 2)
            } else {
                $null
            }
        }
    }

$resumen | Export-Csv -NoTypeInformation -Encoding UTF8 `
    (Join-Path $Salida "resumen.csv")
$resumen | Format-Table -AutoSize
