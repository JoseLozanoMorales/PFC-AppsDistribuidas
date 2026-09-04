# Experimento real (Paso 8) contra los microservicios — guía de arranque

Complementa a `run_paso8.py` (simulación local en SQLite, ya corrida). Esta
versión pega contra el stack real vía el API Gateway, con Locust como
generador de carga, para validar los mismos 24 condiciones con datos de un
sistema real en vez de una simulación.

## Alcance acordado (recorte por ventana de tiempo, ver conversación)

- 24 condiciones = 2 estrategias (`COORD=2pc|saga`) x 4 concurrencias
  (50/100/200/400) x 3 modos de pasarela (`none`/`omission`/`timing`).
- 5 repeticiones por condición = **120 corridas**.
- Cada corrida: 60s de calentamiento descartado + 90s de medición = 150s.
- Tiempo mínimo de carga: **5 horas** (120 × 150s), más reinicios,
  autenticación y cambios de `COORD`. La ejecución real puede superar 5.4h.
- Desviación documentada frente a la guía: la guía sugiere corridas de 5
  minutos (~10h de máquina); se usan corridas de 2m30s por la ventana
  disponible hasta el viernes. Esto debe quedar explícito en el documento de
  amenazas a la validez.

## Orden de ejecución (fijo, no configurable desde CLI)

`repeticion` (1..5) > `coord` (2pc, saga) > `fallo` (none, omission, timing) >
`concurrencia` (50,100,200,400). Repetición es el nivel más externo a
propósito: si el proceso se corta a mitad, el resultado es N repeticiones
**completas** de las 24 condiciones (n más chico pero analizable), nunca
condiciones enteras en cero — en particular `timing` (el modo que nunca se
midió antes por el bug del delay) ya queda cubierto desde la primera
repetición, no se deja para el final.

## Piezas nuevas

| Archivo | Qué hace |
|---|---|
| `generate_request_bank.py` | Registra hasta N usuarios sintéticos reales (login, dirección, método de pago) y escribe el JSON que consume el resto. No pre-llena el carrito. |
| `checkout_locustfile.py` | Tarea de Locust: agrega ítem al carrito + checkout, en bucle, con el modo de fallo sorteado en el cliente (p=0.10) y re-login automático si el JWT expira (10 min). |
| `reset_ambiente.py` | Repone el stock de los productos del experimento a un piso alto (`productos.producto` **y** `inventario.inventario_producto`) vía SQL directo, entre corridas. |
| `run_real_experiment.py` | Orquestador: preflight, orden fijo, reanudación desde el CSV crudo, reinicio del entorno, caché JWT segura por expiración, dos procesos Locust por corrida, monitoreo de CPU/mem de Locust y captura de saturación. |
| `analyze_real_results.py` | Valida las 120 corridas y genera medianas por cada una de las 24 condiciones. |

## Antes de arrancar de verdad

1. **Instalar dependencias en un venv del repo** (no reutilizar otro
   proyecto — regla ya establecida en `spark/PLAN-PASO6.md`):
   ```powershell
   python -m venv experiments/paso8/.venv-real
   experiments/paso8/.venv-real/Scripts/pip install -r experiments/paso8/requirements-real.txt
   ```
2. **Verificar que el cluster de AWS esté encendido** y que
   `CRDB_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` apunten a él en el entorno
   del shell (las mismas variables que usan los microservicios).
3. **Levantar el stack con las dos desviaciones deliberadas activas**
   (ninguna es el default de `docker-compose.yml`):
   ```powershell
   $env:EXPERIMENT_FAULT_INJECTION_ENABLED = "true"
   $env:GATEWAY_RATE_LIMIT_REQUESTS = "20000"
   $env:COORD = "2pc"
   docker compose up -d
   ```
   `run_real_experiment.py` se niega a arrancar si no detecta estas dos
   variables en los contenedores corriendo (`verificar_fault_injection_habilitado`,
   `verificar_rate_limit_elevado`) — no es un chequeo cosmético, evita medir
   5.4h de nada.
4. **Elegir los productos del experimento** (necesitan stock alto y estable):
   ```powershell
   experiments/paso8/.venv-real/Scripts/python -c "from experiments.paso8.generate_request_bank import obtener_productos_con_stock; print(obtener_productos_con_stock('http://localhost:8180', 1, 3))"
   ```
   anota los `producto_id` que devuelva, y repóneles stock alto:
   ```powershell
   experiments/paso8/.venv-real/Scripts/python experiments/paso8/reset_ambiente.py --producto-ids <id1> <id2> <id3> --valor 1000000
   ```
5. **Generar el banco de usuarios sintéticos** (400 para cubrir la
   concurrencia más alta; tarda varios minutos por el pacing del rate-limit):
   ```powershell
   experiments/paso8/.venv-real/Scripts/python experiments/paso8/generate_request_bank.py `
     --gateway http://localhost:8180 `
     --output experiments/paso8/resultados-reales/banco.json `
     --count 400 --min-stock-producto 200000
   ```
   Revisar `banco.fallidos.json` si el conteo final es menor a 400.
6. **Prueba de humo: UNA sola corrida pequeña antes de comprometer 5.4h.**
   ```powershell
   experiments/paso8/.venv-real/Scripts/python experiments/paso8/run_real_experiment.py `
     --admin-token $env:ADMIN_JWT `
     --request-bank experiments/paso8/resultados-reales/banco.json `
     --output experiments/paso8/resultados-reales `
     --concurrencias 50 --repeticiones 1 --warmup-seconds 10 --measure-seconds 20 `
     --producto-ids <id1> <id2> <id3>
   ```
   Esto solo corre `fallo=none, coord=2pc, c=50, r=1` (falta todo lo demás,
   así que el resto queda "pendiente" — es intencional, es la prueba de
   humo). Verificar en `resultados-reales/runs/.../medicion.log` y
   `medicion_stats.csv` que hubo checkouts exitosos reales antes de lanzar el
   experimento completo.
7. **Lanzar el experimento completo** (mismo comando, sin recortar
   `--concurrencias`/`--repeticiones`/tiempos):
   ```powershell
   experiments/paso8/.venv-real/Scripts/python experiments/paso8/run_real_experiment.py `
     --admin-token $env:ADMIN_JWT `
     --request-bank experiments/paso8/resultados-reales/banco.json `
     --output experiments/paso8/resultados-reales `
     --producto-ids <id1> <id2> <id3>
   ```
   Si se corta, correr **exactamente el mismo comando**: lee
   `experimento_real_crudo.csv`, salta lo ya hecho y sigue en el mismo orden.

## Decisiones tomadas que no estaban explícitas en el pedido (revisar)

- **`wait_time` entre iteraciones de cada usuario virtual**: `between(0.2, 1.0)` s,
  elegido para sostener throughput sin ser una f ráfaga sin pausa. No viene de
  la guía ni de la conversación — es un supuesto metodológico a documentar en
  amenazas a la validez si no se ajusta.
- **`spawn_rate` limitado a 20 usuarios/s** para evitar que la máquina de carga
  falsee el resultado con una estampida instantánea.
- **Reinicio real entre corridas**: se reinician usuarios, productos,
  inventario, ventas, pedidos y gateway; después se espera estado saludable y
  se repone el stock. CockroachDB en AWS no se reinicia ni destruye.
- **Caché JWT**: como los tokens duran 10 minutos, solo se reutilizan cuando su
  `exp` cubre calentamiento + medición + 60s de margen; los demás se renuevan
  mediante el login real antes de medir.
- **No se verificó** si `cedula`/`telefono` tienen validación de formato más
  allá de longitud — `generate_request_bank.py` seguirá con los fallidos
  registrados en `banco.fallidos.json` si algún patrón es rechazado; revisar
  ese archivo tras el paso 5.
