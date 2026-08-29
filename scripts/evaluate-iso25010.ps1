param(
    [string]$GatewayUrl = "http://localhost:8180",
    [ValidateRange(1, 86400)][int]$DurationSeconds = 3600,
    [ValidateRange(1, 60)][int]$IntervalSeconds = 1,
    [string]$OutputDirectory = "docs/experimentos/resultados/iso25010",
    [switch]$AllowNonOfficialRun
)

$ErrorActionPreference = "Stop"
if ($DurationSeconds -ne 3600 -and -not $AllowNonOfficialRun) {
    throw "La medicion oficial exige exactamente 3600 segundos. Use -AllowNonOfficialRun solo para validar el recolector."
}

$runKind = if ($DurationSeconds -eq 3600) { "OFICIAL" } else { "NO_OFICIAL" }
$stamp = Get-Date -Format "yyyy-MM-ddTHH-mm-ss"
$runDir = Join-Path $OutputDirectory $stamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$samplesPath = Join-Path $runDir "uptime-samples.csv"
$summaryPath = Join-Path $runDir "uptime-summary.csv"

$samples = [System.Collections.Generic.List[object]]::new()
$started = Get-Date
$deadline = $started.AddSeconds($DurationSeconds)
while ((Get-Date) -lt $deadline) {
    $sampleStarted = Get-Date
    $status = 0
    $success = $false
    $errorMessage = ""
    try {
        $response = Invoke-WebRequest -Uri "$($GatewayUrl.TrimEnd('/'))/api/productos?page=0&size=1" -TimeoutSec 10 -UseBasicParsing
        $status = [int]$response.StatusCode
        $success = $status -ge 200 -and $status -lt 500
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        $errorMessage = $_.Exception.Message
    }
    $samples.Add([pscustomobject]@{
        timestamp = $sampleStarted.ToString("o")
        status = $status
        success = $success
        response_time_ms = [math]::Round(((Get-Date) - $sampleStarted).TotalMilliseconds, 3)
        error = $errorMessage
    })
    $remaining = $IntervalSeconds - ((Get-Date) - $sampleStarted).TotalSeconds
    if ($remaining -gt 0) { Start-Sleep -Milliseconds ([int]($remaining * 1000)) }
}

$ended = Get-Date
$samples | Export-Csv -NoTypeInformation -Encoding utf8 -Path $samplesPath
$successful = @($samples | Where-Object success).Count
$total = $samples.Count
$uptime = if ($total -gt 0) { [math]::Round(100.0 * $successful / $total, 6) } else { 0 }
$summary = [pscustomobject]@{
    run_kind = $runKind
    gateway_url = $GatewayUrl
    started_at = $started.ToString("o")
    ended_at = $ended.ToString("o")
    requested_duration_seconds = $DurationSeconds
    elapsed_seconds = [math]::Round(($ended - $started).TotalSeconds, 3)
    samples_total = $total
    samples_successful = $successful
    uptime_percent = $uptime
    objective_percent = 99.5
    status = if ($runKind -ne "OFICIAL") { "NO_OFICIAL" } elseif ($uptime -ge 99.5) { "CUMPLE" } else { "NO CUMPLE" }
}
$summary | Export-Csv -NoTypeInformation -Encoding utf8 -Path $summaryPath
$summary | Format-List

