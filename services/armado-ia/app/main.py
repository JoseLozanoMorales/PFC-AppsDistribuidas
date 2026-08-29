import json
import logging
import time
from datetime import UTC, datetime

from fastapi import Depends, FastAPI
from prometheus_client import Counter, Gauge, Histogram
from prometheus_fastapi_instrumentator import Instrumentator

from app import armado_service
from app.clients.producto_client import estado_circuit_breaker
from app.config import settings
from app.errors import registrar_exception_handlers
from app.explicacion.bedrock_client import BedrockExplicacionClient
from app.explicacion.client import ExplicacionClient
from app.explicacion.fallback_client import DeterministicExplicacionClient
from app.explicacion.service import ExplicacionService
from app.schemas import AnalizarRequest, AnalizarResponse
from app.security import IdentidadOpcional, identidad_opcional


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "service": "tiendatech-armado-ia",
            "logger": record.name,
            "message": record.getMessage(),
        }
        for field in ("method", "route", "status", "response_time_ms"):
            value = getattr(record, field, None)
            if value is not None:
                payload[field] = value
        return json.dumps(payload, ensure_ascii=False)


handler = logging.StreamHandler()
handler.setFormatter(JsonFormatter())
logging.basicConfig(level=logging.INFO, handlers=[handler], force=True)
http_logger = logging.getLogger("tiendatech.http")
SERVICE_NAME = "tiendatech-armado-ia"
active_connections = Gauge("active_connections", "Solicitudes HTTP activas", ["service"])
request_count = Counter(
    "request_count",
    "Total de solicitudes HTTP",
    ["service", "method", "route", "status"],
)
request_duration_seconds = Histogram(
    "request_duration_seconds",
    "Duracion de solicitudes HTTP en segundos",
    ["service", "method", "route", "status"],
    buckets=(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0),
)

app = FastAPI(title="tiendatech-armado-ia")
registrar_exception_handlers(app)

# D6.1: expone /metrics con http_requests_total y http_request_duration_seconds
# (nombres literales, sin traducir -- a diferencia del lado Java con Micrometer,
# esta libreria ya usa exactamente los nombres que pide la rubrica). Metricas
# de negocio propias en app/metrics.py, registradas en el mismo REGISTRY global.
Instrumentator().instrument(app).expose(app)


@app.middleware("http")
async def observabilidad_http(request, call_next):
    labels = active_connections.labels(service="tiendatech-armado-ia")
    labels.inc()
    started = time.perf_counter()
    status = 500
    try:
        response = await call_next(request)
        status = response.status_code
        return response
    finally:
        elapsed = time.perf_counter() - started
        elapsed_ms = round(elapsed * 1000, 3)
        labels.dec()
        route_obj = request.scope.get("route")
        route = getattr(route_obj, "path", request.url.path)
        if route != "/metrics":
            metric_labels = {
                "service": SERVICE_NAME,
                "method": request.method,
                "route": route,
                "status": str(status),
            }
            request_count.labels(**metric_labels).inc()
            request_duration_seconds.labels(**metric_labels).observe(elapsed)
        http_logger.info(
            "http_request_completed",
            extra={
                "method": request.method,
                "route": route,
                "status": status,
                "response_time_ms": elapsed_ms,
            },
        )


def _crear_explicacion_client() -> ExplicacionClient | None:
    if settings.explicacion.proveedor == "bedrock":
        return BedrockExplicacionClient()
    return None


explicacion_service = ExplicacionService(_crear_explicacion_client(), DeterministicExplicacionClient())


@app.get("/actuator/health")
def health():
    return {"status": "UP"}


# Aprovecha el listener de pybreaker (ver clients/producto_client.py) para
# exponer el estado del circuit breaker -- equivalente a
# /actuator/circuitbreakers de la version Java, evidencia de resiliencia.
@app.get("/actuator/circuitbreakers")
def circuitbreakers():
    return estado_circuit_breaker()


# Identidad opcional (ver security.py): el gateway protege /api/** asi que en
# la practica siempre llega, pero este endpoint no persiste nada por usuario
# y no bloquea si faltara. Si viene, se usa en el log.
@app.post("/api/armado/analizar", response_model=AnalizarResponse, response_model_by_alias=True)
def analizar(request: AnalizarRequest, identidad: IdentidadOpcional = Depends(identidad_opcional)):
    return armado_service.analizar(request, identidad, explicacion_service)
