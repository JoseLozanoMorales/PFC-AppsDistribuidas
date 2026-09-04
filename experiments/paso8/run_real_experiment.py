#!/usr/bin/env python3
"""Orquestador del experimento real (Paso 8) contra los microservicios vía Locust.

Complementa a run_paso8.py (simulación local en SQLite): esta version pega contra
el stack real via el API Gateway, con Locust como generador de carga.

Diseno:
- 24 condiciones = 2 estrategias (COORD) x 4 concurrencias x 3 modos de pasarela.
- 5 repeticiones por condicion = 120 corridas.
- Orden de ejecucion FIJO y deliberado (ver `condiciones_en_orden`): REPETICION
  primero (1..5), y dentro de cada repeticion las 24 condiciones completas
  (coord > fallo > concurrencia). Si el proceso se corta a mitad, el resultado
  es N repeticiones completas de las 24 condiciones (analizable con menos
  potencia estadistica), nunca condiciones enteras en cero -- en particular
  'timing' (el modo que nunca se midio antes por el bug del delay) se cubre ya
  en la primera repeticion, no se deja para el final.
- Reanudable: antes de cada corrida se consulta el CSV crudo ya escrito y se
  saltan las condiciones ya hechas. Cada corrida se escribe (append + flush) al
  terminar, nunca al final del experimento completo. La granularidad de perdida
  ante un corte es de una sola corrida (<=150s), no del experimento completo.
- CPU/memoria del PROCESO de Locust (no de los contenedores) se muestrea cada
  ~2s con psutil mientras la corrida esta activa.
- Se capturan los avisos propios de Locust sobre saturacion del generador
  (ver AVISOS_SATURACION) desde su log, y el conteo de usuarios que realmente
  llego a spawnear vs los pedidos.
"""

from __future__ import annotations

import argparse
import base64
import csv
import json
import os
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import psutil

ROOT = Path(__file__).resolve().parents[2]

COORDS = ["2pc", "saga"]
CONCURRENCIAS = [50, 100, 200, 400]
FALLOS_PRIORIDAD = ["none", "omission", "timing"]  # orden de importancia, fijo, no configurable

CSV_FIELDS = [
    "fallo",
    "coord",
    "concurrencia",
    "repeticion",
    "warmup_seconds",
    "measure_seconds",
    "fault_probability",
    "delay_seconds",
    "usuarios_objetivo",
    "usuarios_spawneados",
    "requests_total",
    "requests_fail",
    "tasa_error",
    "checkout_total",
    "checkout_fail",
    "checkout_confirmadas",
    "tasa_abortos_checkout",
    "codigos_http_json",
    "latencia_p50_ms",
    "latencia_p95_ms",
    "latencia_p99_ms",
    "throughput_rps",
    "locust_cpu_pct_media",
    "locust_cpu_pct_max",
    "locust_mem_mb_media",
    "locust_mem_mb_max",
    "locust_warning_detected",
    "locust_warning_texto",
    "inicio_epoch",
    "duracion_segundos",
]

AVISOS_SATURACION = (
    "cpu usage above",
    "not have enough",
    "hatching is slow",
    "workers exceeded",
    "loadgen",
    "greenlets",
    "significantly reduces",
    "not reaching",
)


def locust_timespan(seconds: float) -> str:
    return f"{int(seconds)}s" if float(seconds).is_integer() else f"{seconds}s"


def leer_condiciones_hechas(csv_path: Path) -> set[tuple[str, str, int, int]]:
    if not csv_path.exists():
        return set()
    hechas = set()
    with csv_path.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            hechas.add((row["fallo"], row["coord"], int(row["concurrencia"]), int(row["repeticion"])))
    return hechas


def escribir_fila(csv_path: Path, row: dict) -> None:
    nuevo = not csv_path.exists()
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    with csv_path.open("a", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=CSV_FIELDS)
        if nuevo:
            writer.writeheader()
        writer.writerow(row)
        fh.flush()
        os.fsync(fh.fileno())


def condiciones_en_orden(concurrencias: list[int], repeticiones: int,
                         coords: list[str] | None = None,
                         fallos: list[str] | None = None):
    """Orden fijo: repeticion > coord > fallo (prioridad) > concurrencia.

    Repeticion es el nivel mas externo a proposito: si el experimento se corta
    a mitad, el resultado es un numero de repeticiones COMPLETAS de las 24
    condiciones (n mas chico pero analizable), en vez de algunas condiciones
    con las 5 repeticiones y otras en cero. coord va antes que fallo dentro de
    cada repeticion para minimizar reinicios de entorno (cambiar COORD exige
    reiniciar tiendatech-pedidos); fallo mantiene su prioridad none>omission>
    timing como desempate dentro de cada bloque de coord.
    """
    coords = coords or COORDS
    fallos = fallos or FALLOS_PRIORIDAD
    for repeticion in range(1, repeticiones + 1):
        for coord in coords:
            for fallo in fallos:
                for concurrencia in concurrencias:
                    yield fallo, coord, concurrencia, repeticion


def env_del_contenedor(nombre_contenedor: str) -> list[str]:
    result = subprocess.run(
        ["docker", "inspect", "--format", "{{json .Config.Env}}", nombre_contenedor],
        capture_output=True, text=True, cwd=ROOT,
    )
    if result.returncode != 0:
        raise SystemExit(f"no se pudo inspeccionar el contenedor {nombre_contenedor}: {result.stderr}")
    return json.loads(result.stdout)


def verificar_fault_injection_habilitado() -> None:
    """Solo ventas-service implementa ExperimentFaultInjector/X-Failure-Mode
    (verificado leyendo el codigo: ni inventario-service ni productos-service
    tienen esa clase). docker-compose.yml solo wirea EXPERIMENT_FAULT_INJECTION_ENABLED
    en tiendatech-ventas (linea 133) -- exigirlo en los otros dos, como hacia
    una version anterior de esta funcion, era un chequeo que nunca se iba a
    cumplir."""
    env_vars = env_del_contenedor("tiendatech-ventas")
    habilitado = any(v == "EXPERIMENT_FAULT_INJECTION_ENABLED=true" for v in env_vars)
    if not habilitado:
        raise SystemExit(
            "EXPERIMENT_FAULT_INJECTION_ENABLED no esta en 'true' en tiendatech-ventas. "
            "Las condiciones omission/timing mediran lo mismo que 'none' sin avisar. "
            "Exporta EXPERIMENT_FAULT_INJECTION_ENABLED=true antes de 'docker compose up' y reinicia ese servicio."
        )
    print("[preflight] EXPERIMENT_FAULT_INJECTION_ENABLED=true confirmado en tiendatech-ventas")


def verificar_rate_limit_elevado(minimo: int = 5000) -> None:
    """El rate-limiter del gateway es por IP. Con el default (300/60s) cualquier
    concurrencia >= ~100 desde una sola maquina generadora se mide a si misma
    (429), no al sistema. Se exige un valor elevado explicito antes de arrancar."""
    env_vars = env_del_contenedor("tiendatech-gateway")
    valor = None
    for v in env_vars:
        if v.startswith("GATEWAY_RATE_LIMIT_REQUESTS="):
            valor = int(v.split("=", 1)[1])
    if valor is None or valor < minimo:
        raise SystemExit(
            f"GATEWAY_RATE_LIMIT_REQUESTS={valor} en tiendatech-gateway, por debajo del minimo {minimo} "
            f"que este experimento necesita (Locust pega desde una sola IP). "
            f"Exporta GATEWAY_RATE_LIMIT_REQUESTS>={minimo} antes de 'docker compose up' y reinicia el gateway. "
            f"Esto es una desviacion deliberada de la config de produccion, documentala como tal."
        )
    print(f"[preflight] GATEWAY_RATE_LIMIT_REQUESTS={valor} (>= {minimo}) confirmado en tiendatech-gateway")


def preflight(gateway: str, admin_token: str) -> dict:
    """Verifica que el gateway y los seis componentes reporten UP, que la inyeccion
    de fallos este realmente encendida y que el rate-limiter no vaya a contaminar
    la medicion, antes de tocar nada."""
    req = urllib.request.Request(
        f"{gateway.rstrip('/')}/api/admin/system",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read())
    except (urllib.error.URLError, urllib.error.HTTPError) as error:
        raise SystemExit(f"preflight fallo: no se pudo contactar {gateway}: {error}") from error
    payload = data.get("data", data)
    down = [s["service"] for s in payload.get("services", []) if s.get("status") != "UP"]
    if down:
        raise SystemExit(f"preflight fallo: servicios caidos: {', '.join(down)}")
    verificar_fault_injection_habilitado()
    verificar_rate_limit_elevado()
    return payload


def set_coord(coord: str, timeout_seconds: float = 90.0) -> None:
    """Reinicia tiendatech-pedidos con COORD=<coord> y espera a que quede healthy.

    docker-compose.yml lee ${COORD:-2pc} del entorno del shell que invoca
    `docker compose`, no del .env del repo, así que hay que pasarlo explícito
    en el entorno de este subprocess.
    """
    env = os.environ.copy()
    env["COORD"] = coord
    subprocess.run(
        ["docker", "compose", "up", "-d", "--force-recreate", "--no-deps", "tiendatech-pedidos"],
        check=True,
        cwd=ROOT,
        env=env,
    )
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        result = subprocess.run(
            ["docker", "inspect", "--format", "{{.State.Health.Status}}", "tiendatech-pedidos"],
            capture_output=True,
            text=True,
            cwd=ROOT,
        )
        if result.stdout.strip() == "healthy":
            return
        time.sleep(3)
    raise SystemExit(f"tiendatech-pedidos no llego a healthy tras cambiar COORD={coord}")


def reiniciar_entorno(timeout_seconds: float = 150.0) -> None:
    """Reinicia servicios de aplicacion para aislar una corrida de la siguiente.

    No reinicia ni destruye CockroachDB: los datos se conservan y el stock se
    repone por SQL. El reinicio limpia colas HTTP, pools, circuit breakers y
    trabajo en vuelo dejado por condiciones de alta concurrencia.
    """
    servicios = [
        "tiendatech-usuarios", "tiendatech-productos", "tiendatech-inventario",
        "tiendatech-ventas", "tiendatech-pedidos", "tiendatech-gateway",
    ]
    subprocess.run(["docker", "restart", *servicios], check=True, cwd=ROOT,
                   stdout=subprocess.DEVNULL)
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        listos = True
        for servicio in servicios:
            result = subprocess.run(
                ["docker", "inspect", "--format",
                 "{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}", servicio],
                capture_output=True, text=True, cwd=ROOT)
            estado, _, salud = result.stdout.strip().partition("|")
            if result.returncode != 0 or estado != "running" or (salud and salud != "healthy"):
                listos = False
                break
        if listos:
            time.sleep(5)
            return
        time.sleep(3)
    raise RuntimeError("el entorno no recupero estado running/healthy tras el reinicio")


class MonitorRecursos:
    """Muestrea CPU% y RSS del proceso de Locust (y sus hijos) cada ~2s en un hilo aparte."""

    def __init__(self, pid: int, intervalo: float = 2.0):
        self._ps = psutil.Process(pid)
        self._intervalo = intervalo
        self._muestras: list[tuple[float, float]] = []
        self._detener = threading.Event()
        self._hilo = threading.Thread(target=self._loop, daemon=True)

    def iniciar(self) -> None:
        self._hilo.start()

    def _procesos_vivos(self) -> list[psutil.Process]:
        try:
            hijos = self._ps.children(recursive=True)
        except psutil.Error:
            hijos = []
        return [p for p in [self._ps, *hijos] if p.is_running()]

    def _loop(self) -> None:
        for proceso in self._procesos_vivos():
            try:
                proceso.cpu_percent(interval=None)  # primera lectura siempre da 0.0, se descarta
            except psutil.Error:
                pass
        while not self._detener.is_set():
            try:
                procesos = self._procesos_vivos()
                cpu = sum(p.cpu_percent(interval=None) for p in procesos)
                mem_mb = sum(p.memory_info().rss for p in procesos) / 1024 / 1024
                self._muestras.append((cpu, mem_mb))
            except psutil.Error:
                pass
            self._detener.wait(self._intervalo)

    def detener(self) -> dict:
        self._detener.set()
        self._hilo.join(timeout=5)
        if not self._muestras:
            return {"cpu_media": 0.0, "cpu_max": 0.0, "mem_media_mb": 0.0, "mem_max_mb": 0.0}
        cpus = [c for c, _ in self._muestras]
        mems = [m for _, m in self._muestras]
        return {
            "cpu_media": round(sum(cpus) / len(cpus), 2),
            "cpu_max": round(max(cpus), 2),
            "mem_media_mb": round(sum(mems) / len(mems), 2),
            "mem_max_mb": round(max(mems), 2),
        }


def lanzar_locust(gateway: str, users: int, spawn_rate: float, run_time_s: float,
                   csv_prefix: Path, log_path: Path, env: dict) -> subprocess.Popen:
    locustfile = Path(__file__).parent / "checkout_locustfile.py"
    cmd = [
        sys.executable, "-m", "locust", "-f", str(locustfile),
        "--host", gateway, "--headless",
        "--users", str(users), "--spawn-rate", str(spawn_rate),
        "--run-time", locust_timespan(run_time_s),
        "--csv", str(csv_prefix), "--csv-full-history",
        "--logfile", str(log_path), "--loglevel", "INFO",
        "--only-summary",
    ]
    process_env = env.copy()
    process_env["RESPONSE_CODES_PATH"] = str(Path(f"{csv_prefix}_response_codes.json"))
    return subprocess.Popen(cmd, cwd=ROOT, env=process_env)


def leer_stats_csv(csv_prefix: Path) -> dict:
    stats_path = Path(f"{csv_prefix}_stats.csv")
    if not stats_path.exists():
        return {}
    with stats_path.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            if row.get("Name") == "Aggregated":
                return row
    return {}


def leer_stat_checkout(csv_prefix: Path) -> dict:
    stats_path = Path(f"{csv_prefix}_stats.csv")
    if not stats_path.exists():
        return {}
    with stats_path.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            if row.get("Name", "").startswith("POST /api/ordenes/checkout"):
                return row
    return {}


def leer_usuarios_spawneados(csv_prefix: Path) -> int:
    history_path = Path(f"{csv_prefix}_stats_history.csv")
    if not history_path.exists():
        return 0
    maximo = 0
    with history_path.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            if row.get("Name") == "Aggregated":
                maximo = max(maximo, int(float(row.get("User Count", 0) or 0)))
    return maximo


def escanear_avisos_saturacion(log_path: Path) -> tuple[bool, str]:
    if not log_path.exists():
        return False, ""
    texto = log_path.read_text(encoding="utf-8", errors="replace").lower()
    encontrados = [aviso for aviso in AVISOS_SATURACION if aviso in texto]
    return bool(encontrados), "; ".join(encontrados)


def preparar_banco_autenticado(gateway: str, banco_path: Path, cantidad: int,
                               salida_path: Path, cache_path: Path,
                               validez_minima_seconds: float,
                               workers: int = 5) -> Path:
    """Usa JWT aun validos y renueva solo los necesarios fuera de medicion.

    Los tokens locales duran diez minutos. Nunca se reutiliza uno si su ``exp``
    no cubre calentamiento + medicion + un margen de seguridad; por tanto, la
    optimizacion no puede convertir una corrida real en una medicion de 401.
    """
    casos = json.loads(banco_path.read_text(encoding="utf-8"))[:cantidad]

    def expira_en(token: str) -> float:
        try:
            payload = token.split(".")[1]
            payload += "=" * (-len(payload) % 4)
            data = json.loads(base64.urlsafe_b64decode(payload.encode("ascii")))
            return float(data.get("exp", 0))
        except (IndexError, ValueError, TypeError, json.JSONDecodeError):
            return 0.0

    cache: dict[str, str] = {}
    if cache_path.exists():
        try:
            cache = json.loads(cache_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            cache = {}
    limite = time.time() + validez_minima_seconds

    def autenticar(caso: dict) -> tuple[int, str]:
        ultimo_error: Exception | None = None
        for intento in range(1, 5):
            try:
                body = json.dumps({
                    "usuario": caso["usuario"],
                    "contrasena": caso["contrasena"],
                }).encode("utf-8")
                req = urllib.request.Request(
                    f"{gateway.rstrip('/')}/api/login", data=body,
                    headers={"Content-Type": "application/json"}, method="POST")
                with urllib.request.urlopen(req, timeout=60) as resp:
                    envelope = json.loads(resp.read())
                data = envelope.get("data", envelope)
                token = data.get("token") or data.get("access")
                if not token:
                    raise RuntimeError(f"login sin token para caso {caso['caseId']}")
                return caso["caseId"], token
            except Exception as error:
                ultimo_error = error
                if intento < 4:
                    time.sleep(2 ** intento)
        raise RuntimeError(
            f"login agotado tras 4 intentos para caso {caso['caseId']}: {ultimo_error}")

    tokens: dict[int, str] = {}
    pendientes: list[dict] = []
    for caso in casos:
        token = cache.get(str(caso["caseId"]), "")
        if expira_en(token) >= limite:
            tokens[caso["caseId"]] = token
        else:
            pendientes.append(caso)

    if tokens:
        print(f"[auth-cache] reutilizados {len(tokens)}/{cantidad} JWT con validez suficiente")
    with ThreadPoolExecutor(max_workers=min(workers, cantidad)) as pool:
        futuros = [pool.submit(autenticar, caso) for caso in pendientes]
        for futuro in as_completed(futuros):
            case_id, token = futuro.result()
            tokens[case_id] = token
    if len(tokens) != cantidad:
        raise RuntimeError(f"solo se autenticaron {len(tokens)}/{cantidad} usuarios")
    for caso in casos:
        caso["token"] = tokens[caso["caseId"]]
        cache[str(caso["caseId"])] = caso["token"]
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    temporal = cache_path.with_suffix(".tmp")
    temporal.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
    temporal.replace(cache_path)
    salida_path.write_text(json.dumps(casos, ensure_ascii=False, indent=2), encoding="utf-8")
    return salida_path


def ejecutar_corrida(args: argparse.Namespace, fallo: str, coord: str, concurrencia: int, repeticion: int) -> dict:
    salida_dir = args.output / "runs" / f"{fallo}-{coord}-c{concurrencia}-r{repeticion}"
    salida_dir.mkdir(parents=True, exist_ok=True)

    env = os.environ.copy()
    banco_fresco = preparar_banco_autenticado(
        args.gateway, args.request_bank, concurrencia, salida_dir / "banco_autenticado.json",
        args.output / "cache_tokens.json",
        args.warmup_seconds + args.measure_seconds + 60.0)
    env["REQUEST_BANK_PATH"] = str(banco_fresco)
    env["FALLO_ACTIVO"] = fallo
    env["FAULT_PROBABILITY"] = str(args.fault_probability)
    # Evita que el login de todos los usuarios ocurra en el mismo segundo. El
    # gateway/usuarios soporta la carga sostenida, pero una estampida de 100-400
    # hashes BCrypt simultaneos agota el timeout antes de empezar a medir.
    spawn_rate = min(20.0, max(5.0, float(concurrencia)))

    # Fase 1: calentamiento real, en un proceso Locust aparte y descartado por completo.
    # Correr el calentamiento como proceso SEPARADO (en vez de resetear stats a mitad
    # de un mismo proceso) evita cualquier ambiguedad sobre si las estadisticas de
    # Locust son acumulativas desde el arranque o por ventana.
    p_warmup = lanzar_locust(args.gateway, concurrencia, spawn_rate, args.warmup_seconds,
                              salida_dir / "warmup", salida_dir / "warmup.log", env)
    warmup_rc = p_warmup.wait()
    # Locust usa rc=1 cuando el sistema bajo prueba devuelve fallos HTTP. Eso
    # es un resultado experimental valido (y esperado en omission/timing).
    # rc>=2 indica fallo de CLI/configuracion/ejecucion del generador.
    if warmup_rc not in (0, 1):
        raise RuntimeError(
            f"Locust fallo durante warmup de {fallo}/{coord}/c{concurrencia}/r{repeticion}: "
            f"exit code {warmup_rc}; ver {salida_dir / 'warmup.log'}"
        )

    # Fase 2: medicion oficial de la corrida.
    inicio_epoch = time.time()
    csv_prefix = salida_dir / "medicion"
    log_path = salida_dir / "medicion.log"
    p_medida = lanzar_locust(args.gateway, concurrencia, spawn_rate, args.measure_seconds,
                              csv_prefix, log_path, env)
    monitor = MonitorRecursos(p_medida.pid)
    monitor.iniciar()
    medida_rc = p_medida.wait()
    recursos = monitor.detener()
    if medida_rc not in (0, 1):
        raise RuntimeError(
            f"Locust fallo durante medicion de {fallo}/{coord}/c{concurrencia}/r{repeticion}: "
            f"exit code {medida_rc}; ver {log_path}"
        )
    duracion = time.time() - inicio_epoch

    stats = leer_stats_csv(csv_prefix)
    checkout_stats = leer_stat_checkout(csv_prefix)
    usuarios_spawneados = leer_usuarios_spawneados(csv_prefix)
    aviso_detectado, aviso_texto = escanear_avisos_saturacion(log_path)

    requests_total = int(stats.get("Request Count", 0) or 0)
    requests_fail = int(stats.get("Failure Count", 0) or 0)
    checkout_total = int(checkout_stats.get("Request Count", 0) or 0)
    checkout_fail = int(checkout_stats.get("Failure Count", 0) or 0)
    if requests_total <= 0:
        raise RuntimeError(
            f"corrida invalida {fallo}/{coord}/c{concurrencia}/r{repeticion}: "
            f"Locust registro cero solicitudes; no se escribira checkpoint"
        )
    if usuarios_spawneados < concurrencia:
        raise RuntimeError(
            f"corrida invalida {fallo}/{coord}/c{concurrencia}/r{repeticion}: "
            f"Locust solo alcanzo {usuarios_spawneados}/{concurrencia} usuarios; "
            f"no se escribira checkpoint"
        )
    # Cero checkouts puede ser un resultado real bajo saturacion: todos los
    # usuarios llegaron a ejecutar el paso previo de carrito? no; el punto es
    # conservar esa evidencia cuando Locust si alcanzo la concurrencia y emitio
    # solicitudes. Los smoke tests ya verifican que el flujo incluye checkout;
    # exigir uno en cada condicion sesgaria el experimento descartando justo las
    # condiciones donde el sistema colapsa antes de confirmar una orden.
    codigos_path = Path(f"{csv_prefix}_response_codes.json")
    codigos = json.loads(codigos_path.read_text(encoding="utf-8")) if codigos_path.exists() else {}

    return {
        "fallo": fallo,
        "coord": coord,
        "concurrencia": concurrencia,
        "repeticion": repeticion,
        "warmup_seconds": args.warmup_seconds,
        "measure_seconds": args.measure_seconds,
        "fault_probability": 0.0 if fallo == "none" else args.fault_probability,
        "delay_seconds": args.delay_seconds,
        "usuarios_objetivo": concurrencia,
        "usuarios_spawneados": usuarios_spawneados,
        "requests_total": requests_total,
        "requests_fail": requests_fail,
        "tasa_error": round(requests_fail / requests_total, 6) if requests_total else 0.0,
        "checkout_total": checkout_total,
        "checkout_fail": checkout_fail,
        "checkout_confirmadas": checkout_total - checkout_fail,
        "tasa_abortos_checkout": round(checkout_fail / checkout_total, 6) if checkout_total else "",
        "codigos_http_json": json.dumps(codigos, sort_keys=True),
        "latencia_p50_ms": stats.get("50%", ""),
        "latencia_p95_ms": stats.get("95%", ""),
        "latencia_p99_ms": stats.get("99%", ""),
        "throughput_rps": stats.get("Requests/s", ""),
        "locust_cpu_pct_media": recursos["cpu_media"],
        "locust_cpu_pct_max": recursos["cpu_max"],
        "locust_mem_mb_media": recursos["mem_media_mb"],
        "locust_mem_mb_max": recursos["mem_max_mb"],
        "locust_warning_detected": aviso_detectado,
        "locust_warning_texto": aviso_texto,
        "inicio_epoch": round(inicio_epoch, 3),
        "duracion_segundos": round(duracion, 3),
    }


def parser() -> argparse.ArgumentParser:
    cli = argparse.ArgumentParser()
    cli.add_argument("--gateway", default="http://localhost:8180")
    cli.add_argument("--admin-token", default=os.environ.get("ADMIN_JWT"),
                      required=os.environ.get("ADMIN_JWT") is None)
    cli.add_argument("--request-bank", type=Path, required=True,
                      help="JSON generado por generate_request_bank.py")
    cli.add_argument("--output", type=Path, default=ROOT / "experiments" / "paso8" / "resultados-reales")
    cli.add_argument("--repeticiones", type=int, default=5)
    cli.add_argument("--concurrencias", type=int, nargs="+", default=CONCURRENCIAS)
    cli.add_argument("--coords", choices=COORDS, nargs="+", default=COORDS,
                     help="subconjunto para smoke tests; por defecto ejecuta 2pc y saga")
    cli.add_argument("--fallos", choices=FALLOS_PRIORIDAD, nargs="+", default=FALLOS_PRIORIDAD,
                     help="subconjunto para smoke tests; por defecto ejecuta los tres modos")
    cli.add_argument("--warmup-seconds", type=float, default=60.0)
    cli.add_argument("--measure-seconds", type=float, default=90.0)
    cli.add_argument("--fault-probability", type=float, default=0.10)
    cli.add_argument("--delay-seconds", type=float, default=5.0)
    cli.add_argument("--seed", type=int, default=2026)
    cli.add_argument("--skip-preflight", action="store_true")
    cli.add_argument("--producto-ids", type=int, nargs="+", default=None,
                      help="productos a reponer entre corridas (ver reset_ambiente.py); "
                           "si se omite, no se repone stock entre corridas")
    cli.add_argument("--stock-tope", type=int, default=1_000_000)
    return cli


def main() -> int:
    args = parser().parse_args()
    crudo_path = args.output / "experimento_real_crudo.csv"
    hechas = leer_condiciones_hechas(crudo_path)
    pendientes = [c for c in condiciones_en_orden(
        args.concurrencias, args.repeticiones, args.coords, args.fallos) if c not in hechas]

    print(f"[resume] {len(hechas)} corridas ya registradas en {crudo_path}, "
          f"{len(pendientes)} pendientes de {len(hechas) + len(pendientes)} totales")

    if not args.skip_preflight:
        preflight(args.gateway, args.admin_token)
        print("[preflight] gateway y seis componentes reportan UP")

    if not pendientes:
        print("nada pendiente, experimento ya completo")
        return 0

    if args.producto_ids:
        from reset_ambiente import topar_stock
        topar_stock(args.producto_ids, args.stock_tope)
        print(f"[reset] stock inicial repuesto a {args.stock_tope} para {args.producto_ids}")

    coord_activo: str | None = None
    inicio_experimento = time.time()
    for i, (fallo, coord, concurrencia, repeticion) in enumerate(pendientes, start=1):
        if coord != coord_activo:
            print(f"[coord] cambiando entorno a COORD={coord} (era {coord_activo})")
            set_coord(coord)
            coord_activo = coord

        reiniciar_entorno()

        if args.producto_ids:
            from reset_ambiente import topar_stock
            topar_stock(args.producto_ids, args.stock_tope)

        t0 = time.time()
        fila = ejecutar_corrida(args, fallo, coord, concurrencia, repeticion)
        escribir_fila(crudo_path, fila)
        transcurrido = time.time() - inicio_experimento
        print(f"[{i}/{len(pendientes)}] fallo={fallo} coord={coord} c={concurrencia} r={repeticion} "
              f"p95={fila['latencia_p95_ms']}ms error={fila['tasa_error']} "
              f"({time.time() - t0:.1f}s esta corrida, {transcurrido / 3600:.2f}h acumuladas)")

    print(f"experimento completo: {len(pendientes)} corridas en {(time.time() - inicio_experimento) / 3600:.2f}h")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
