"""
Puerto para redactar EN LENGUAJE NATURAL un resultado ya calculado. Nunca
decide el porcentaje ni la recomendacion (eso es domain/bottleneck.py y
domain/recomendador.py, 100% deterministicos). Cambiar de proveedor de LLM
es implementar este Protocol y activarlo por
settings.explicacion.proveedor -- ver bedrock_client.py.
"""
from dataclasses import dataclass, field
from typing import Protocol

from app.schemas import RecomendacionResponse


@dataclass(frozen=True)
class ContextoExplicacion:
    porcentaje_bottleneck: float
    nivel: str
    componente_limitante: str
    advertencias: list[str]
    componentes_nombres: dict[str, str]
    recomendacion: RecomendacionResponse | None = field(default=None)


class ExplicacionClient(Protocol):
    def explicar(self, contexto: ContextoExplicacion) -> str: ...
