# Observabilidad distribuida - preparación del Paso 3

## Componentes preparados

- Logs JSON de los seis microservicios Java y `armado-ia`.
- Campos HTTP: `timestamp`, `service`, `method`, `route`, `status` y `response_time_ms`.
- `/metrics` en cada microservicio.
- Métricas requeridas: `request_count_total`, `request_duration_seconds` y `active_connections`.
- Prometheus local con scraping cada 15 segundos.
- Grafana Alloy para envío opcional a Grafana Cloud.
- Dashboard importable con RPS, P50/P95/P99, errores 4xx/5xx y conexiones activas.
- Locust configurado para 50 usuarios durante 60 segundos.

## Prometheus local

```powershell
docker compose up -d --build
docker compose ps
```

Prometheus queda disponible en `http://localhost:9090`. En **Status > Targets** deben aparecer siete targets `UP`.

## Grafana local (item 5, Paso 10)

```powershell
docker compose up -d tiendatech-grafana
```

Grafana queda disponible en `http://localhost:3000` (usuario/clave por defecto
`admin`/`admin`, variables `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD` en
`.env`). El datasource de Prometheus y el dashboard **se cargan solos por
provisioning** desde `ops/observability/grafana/provisioning/` y
`ops/observability/grafana-dashboard.json` — no hace falta importarlo a mano;
el panel abre directamente en **Dashboards > TiendaTech - Observabilidad
distribuida**. Antes de una corrida de carga, abrir el dashboard con rango
`Last 15 minutes` (o el que use la prueba) y capturar la pantalla con datos
reales una vez termine, como evidencia del item 5.

## Grafana Cloud

Crear un stack gratuito y copiar las credenciales de Prometheus Remote Write en variables locales, sin guardarlas en Git:

```powershell
$env:GRAFANA_CLOUD_PROM_URL="https://prometheus-...grafana.net/api/prom/push"
$env:GRAFANA_CLOUD_PROM_USERNAME="123456"
$env:GRAFANA_CLOUD_API_KEY="<API_KEY>"
docker compose --profile grafana-cloud up -d tiendatech-alloy
```

Importar `ops/observability/grafana-dashboard.json` desde **Dashboards > New > Import**. Alloy reemplaza al antiguo Grafana Agent y usa el mismo flujo de remote write solicitado por la guía.

## Prueba de carga oficial

```powershell
python -m pip install -r tests/load/requirements.txt
./tests/load/run-load-test.ps1
```

La ejecución predeterminada usa 50 usuarios, una rampa de 5 usuarios/s y una duración de 60 segundos. Antes de iniciarla, abrir el dashboard de Grafana con rango `Last 15 minutes`; durante la ejecución se debe capturar la pantalla con datos reales.

Los resultados CSV y HTML se generan en `tests/load/results/`. Estos archivos deben analizarse para determinar P95, tasa 5xx y cuello de botella. No se incluyen valores simulados en el repositorio.

## Credenciales pendientes

La conexión real a Grafana Cloud no puede verificarse hasta disponer de `GRAFANA_CLOUD_PROM_URL`, `GRAFANA_CLOUD_PROM_USERNAME` y `GRAFANA_CLOUD_API_KEY`. El perfil `grafana-cloud` permanece desactivado por defecto para que el stack local no falle cuando esas variables aún no existen.
