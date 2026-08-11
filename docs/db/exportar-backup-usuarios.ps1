param(
    [Parameter(Mandatory = $true)] [string]$Backup,
    [string]$Salida = "$PSScriptRoot/../../tmp/import-usuarios.sql",
    [string]$Database = "tiendatech",
    [string]$PgRestore = "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"
)
$ErrorActionPreference = "Stop"
$temporal = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../../tmp/usuarios-export.sql"))
$orden = @("usuarios.rol","usuarios.provincia","usuarios.ciudad","usuarios.usuario","usuarios.direccion","usuarios.usuario_auditoria","usuarios.auth_refresh_tokens")
try {
  & $PgRestore --data-only --no-owner --no-privileges --file=$temporal $Backup
  if($LASTEXITCODE -ne 0){throw "pg_restore fallo: $LASTEXITCODE"}
  $bloques=@{};$actual=$null
  foreach($linea in [IO.File]::ReadLines($temporal)){
    if(-not $actual -and $linea -match '^COPY (usuarios\.[^ ]+) \((.+)\) FROM stdin;$'){
      if($matches[1] -notin $orden){continue};$actual=$matches[1]
      $bloques[$actual]=[pscustomobject]@{Columnas=$matches[2];Filas=[Collections.Generic.List[string]]::new()};continue
    }
    if(-not $actual){continue};if($linea -eq '\.'){$actual=$null;continue};$bloques[$actual].Filas.Add($linea)
  }
  $w=[IO.StreamWriter]::new($Salida,$false,[Text.UTF8Encoding]::new($false))
  try{
    $w.WriteLine("USE $Database;")
    foreach($tabla in $orden){
      if(-not $bloques[$tabla] -or $bloques[$tabla].Filas.Count -eq 0){continue};$b=$bloques[$tabla]
      if($tabla -eq 'usuarios.usuario'){
        $w.WriteLine("DROP TABLE IF EXISTS usuarios.usuario_import; CREATE TABLE usuarios.usuario_import AS SELECT * FROM usuarios.usuario WHERE false;")
        $w.WriteLine("COPY usuarios.usuario_import ($($b.Columnas)) FROM STDIN;");foreach($f in $b.Filas){$w.WriteLine($f)};$w.WriteLine('\.')
        $w.WriteLine("UPSERT INTO usuarios.usuario ($($b.Columnas)) SELECT $($b.Columnas) FROM usuarios.usuario_import; DROP TABLE usuarios.usuario_import;")
      }else{
        $w.WriteLine("COPY $tabla ($($b.Columnas)) FROM STDIN;");foreach($f in $b.Filas){$w.WriteLine($f)};$w.WriteLine('\.')
      }
    }
    $secuencias=@(
      @('rol_rol_id_seq','rol','rol_id'),@('provincia_provincia_id_seq','provincia','provincia_id'),
      @('ciudad_ciudad_id_seq','ciudad','ciudad_id'),@('usuario_usuario_id_seq','usuario','usuario_id'),
      @('direccion_direccion_id_seq','direccion','direccion_id'),@('usuario_auditoria_id_sesion_seq','usuario_auditoria','id_sesion'))
    foreach($x in $secuencias){$w.WriteLine("SELECT setval('usuarios.$($x[0])',coalesce((SELECT max($($x[2])) FROM usuarios.$($x[1])),1));")}
  }finally{$w.Dispose()}
}finally{if(Test-Path $temporal){Remove-Item -LiteralPath $temporal -Force}}
Write-Output "Importacion de Usuarios generada en: $Salida"
