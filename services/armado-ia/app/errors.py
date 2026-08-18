"""
Excepciones de dominio y handlers de FastAPI que producen el mismo shape de
ErrorResponse que usaba la version Java (timestamp, status, error, message,
path) para no romper a ningun consumidor del contrato de error.
"""
import logging
from datetime import datetime, timezone

import pybreaker
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

REASON_PHRASES = {
    400: "Bad Request",
    404: "Not Found",
    500: "Internal Server Error",
    503: "Service Unavailable",
}


class DominioError(Exception):
    status_code = 500

    def __init__(self, message: str):
        self.message = message
        super().__init__(message)


class BadRequestError(DominioError):
    status_code = 400


class NotFoundError(DominioError):
    status_code = 404


def _now_iso_millis() -> str:
    # Mismo formato que Instant.now() serializado por Jackson en la version
    # Java: ISO-8601 con milisegundos y sufijo 'Z' (no +00:00, no microsegundos).
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.") \
        + f"{datetime.now(timezone.utc).microsecond // 1000:03d}Z"


def _build_response(status_code: int, message: str, path: str) -> JSONResponse:
    body = {
        "timestamp": _now_iso_millis(),
        "status": status_code,
        "error": REASON_PHRASES.get(status_code, "Error"),
        "message": message,
        "path": path,
    }
    return JSONResponse(status_code=status_code, content=body)


def registrar_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(DominioError)
    async def _dominio_error_handler(request: Request, exc: DominioError):
        return _build_response(exc.status_code, exc.message, request.url.path)

    @app.exception_handler(pybreaker.CircuitBreakerError)
    async def _circuit_breaker_handler(request: Request, exc: pybreaker.CircuitBreakerError):
        return _build_response(503, "Servicio dependiente no disponible temporalmente "
                                     "(circuit breaker abierto)", request.url.path)

    @app.exception_handler(RequestValidationError)
    async def _validation_handler(request: Request, exc: RequestValidationError):
        detalles = "; ".join(
            f"{'.'.join(str(p) for p in err['loc'] if p != 'body')}: {err['msg']}"
            for err in exc.errors()
        )
        return _build_response(400, detalles or "Datos de la solicitud invalidos", request.url.path)

    @app.exception_handler(StarletteHTTPException)
    async def _http_exception_handler(request: Request, exc: StarletteHTTPException):
        if exc.status_code == 404:
            return _build_response(404, f"Recurso no encontrado: {request.url.path}", request.url.path)
        return _build_response(exc.status_code, str(exc.detail), request.url.path)

    @app.exception_handler(Exception)
    async def _generic_handler(request: Request, exc: Exception):
        logging.getLogger("armado_ia").exception("Error inesperado en %s", request.url.path)
        return _build_response(500, "Ha ocurrido un error interno", request.url.path)
