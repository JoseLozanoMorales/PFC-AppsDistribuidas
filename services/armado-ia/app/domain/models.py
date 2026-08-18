from dataclasses import dataclass, field
from decimal import Decimal


@dataclass(frozen=True)
class ProductoCatalogo:
    id: int
    nombre: str
    precio: Decimal
    categoria_id: int | None
    categoria_nombre: str | None
    habilitado: bool
    atributos: dict = field(default_factory=dict)

    def atributo_numerico(self, clave: str) -> float | None:
        valor = self.atributos.get(clave)
        if isinstance(valor, bool):
            return None
        if isinstance(valor, (int, float)):
            return float(valor)
        return None

    def atributo_texto(self, clave: str) -> str | None:
        valor = self.atributos.get(clave)
        return None if valor is None else str(valor)


@dataclass(frozen=True)
class CategoriaInfo:
    id: int
    nombre: str
