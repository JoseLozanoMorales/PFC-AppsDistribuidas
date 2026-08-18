"""
Modelos de request/response. Atributos Python en snake_case, JSON en
camelCase via alias_generator=to_camel -- mismo shape que devolvia Jackson en
la version Java (porcentajeCuelloBotella, componenteLimitante, etc.), para no
romper el contrato de la API.
"""
from decimal import Decimal

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ComponenteResponse(CamelModel):
    id: int
    nombre: str
    # float, no Decimal: Pydantic serializa Decimal como STRING JSON por
    # defecto ("470.0"), pero Jackson en la version Java serializaba
    # BigDecimal como NUMERO JSON (470.0 sin comillas) -- verificado en vivo.
    # float preserva el mismo shape de contrato.
    precio: float


class RecomendacionResponse(CamelModel):
    presupuesto_usado: float
    componentes: dict[str, ComponenteResponse]
    porcentaje_cuello_botella: float | None = None
    nivel_cuello_botella: str | None = None
    componente_limitante: str | None = None
    advertencias: list[str] = []


class AnalizarRequest(CamelModel):
    componentes: dict[str, int | None]
    presupuesto_maximo: Decimal | None = None


class AnalizarResponse(CamelModel):
    porcentaje_cuello_botella: float
    nivel: str
    componente_limitante: str
    explicacion: str
    advertencias: list[str]
    recomendacion: RecomendacionResponse | None = None
