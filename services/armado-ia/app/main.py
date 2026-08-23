import logging

from fastapi import Depends, FastAPI

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

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s -- %(message)s")

app = FastAPI(title="armado-ia-service")
registrar_exception_handlers(app)


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
