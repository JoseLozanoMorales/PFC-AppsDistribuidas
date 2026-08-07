param(
    [ValidateSet(1, 2, 4, 8)]
    [int]$Workers = 4,
    [int]$Repeticion = 1,
    [string]$Salida = "pyspark",
    [string]$RedDocker = "pfc-appsdistribuidas_pfc-net"
)

$ErrorActionPreference = "Stop"
$raiz = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jdbc = Join-Path $env:USERPROFILE ".m2\repository\org\postgresql\postgresql\42.7.4\postgresql-42.7.4.jar"

if (-not (Test-Path -LiteralPath $jdbc)) {
    throw "No se encontró el controlador JDBC en $jdbc"
}

docker build -t tiendatech-spark:3.5.5 -f (Join-Path $PSScriptRoot "Dockerfile") $raiz
if ($LASTEXITCODE -ne 0) {
    throw "No fue posible construir la imagen de Spark."
}

$salidaContenedor = "/workspace/spark/out/$Salida"
docker run --rm `
    --network $RedDocker `
    -v "${raiz}:/workspace" `
    -v "${jdbc}:/opt/jdbc/postgresql.jar:ro" `
    tiendatech-spark:3.5.5 `
    /opt/spark/bin/spark-submit `
    --master "local[$Workers]" `
    --jars /opt/jdbc/postgresql.jar `
    /workspace/spark/pipeline.py `
    --master "local[$Workers]" `
    --jdbc-url "jdbc:postgresql://crdb-1:26257/tiendatech?sslmode=disable" `
    --jdbc-jar /opt/jdbc/postgresql.jar `
    --output $salidaContenedor `
    --run $Repeticion `
    --overwrite

if ($LASTEXITCODE -ne 0) {
    throw "La ejecución PySpark terminó con error."
}
