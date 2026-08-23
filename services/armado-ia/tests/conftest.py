"""
Fixtures compartidas para los tests de dominio (recomendador.py, bottleneck.py).

CatalogoStub implementa CatalogoProvider (app/domain/catalogo_provider.py) en
memoria, sin red ni mocks de HTTP: cumple el Protocol solo por tener los dos
metodos con la firma correcta (duck typing estructural), exactamente igual
que producto_client en produccion (ver ADR-008).
"""
from decimal import Decimal

import pytest

from app.config import settings
from app.domain.models import CategoriaInfo, ProductoCatalogo

# IDs de categoria arbitrarios pero estables para los tests, uno por cada key
# que usa settings.categorias. recomendador._resolver_categoria_ids empareja
# por 'nombre' (case-insensitive) contra settings.categorias[key].nombre, asi
# que categoria_info() siempre usa el nombre real de la config -- si alguien
# cambia un nombre en config.py, estos tests fallan en vez de quedar
# silenciosamente desincronizados.
CATEGORIA_ID: dict[str, int] = {
    "cpu": 1,
    "mobo": 2,
    "ram": 3,
    "storage": 4,
    "gpu": 5,
    "psu": 6,
    "case": 7,
    "cooling": 8,
    "periferico": 9,
}


def categoria_info(key: str) -> CategoriaInfo:
    return CategoriaInfo(id=CATEGORIA_ID[key], nombre=settings.categorias[key].nombre)


def producto(id: int, categoria_key: str, precio, nombre: str = "Producto",
             habilitado: bool = True, **atributos) -> ProductoCatalogo:
    """Construye un ProductoCatalogo de prueba. atributos se pasa tal cual al
    dict 'atributos' (mismo formato que expone productos-service)."""
    return ProductoCatalogo(
        id=id,
        nombre=nombre,
        precio=Decimal(str(precio)),
        categoria_id=CATEGORIA_ID[categoria_key],
        categoria_nombre=settings.categorias[categoria_key].nombre,
        habilitado=habilitado,
        atributos=atributos,
    )


class CatalogoStub:
    """Catalogo en memoria. No hereda de CatalogoProvider ni lo importa --
    cumple el Protocol por estructura, para que el test sea representativo de
    como se comporta el Protocol en produccion (duck typing, no herencia)."""

    def __init__(self, categorias: list[CategoriaInfo],
                 productos: dict[int, list[ProductoCatalogo]] | None = None):
        self._categorias = categorias
        self._productos = productos or {}

    def listar_categorias(self) -> list[CategoriaInfo]:
        return self._categorias

    def listar_por_categoria(self, categoria_id: int) -> list[ProductoCatalogo]:
        return self._productos.get(categoria_id, [])


def catalogo_de(*productos_por_key: tuple[str, list[ProductoCatalogo]]) -> CatalogoStub:
    """
    Construye un CatalogoStub a partir de pares (categoria_key, [productos]).
    Solo incluye en listar_categorias() las categorias mencionadas -- una
    categoria ausente aqui es indistinguible, desde el punto de vista del
    recomendador, de una categoria que productos-service todavia no expone.

    Ejemplo: catalogo_de(("cpu", [cpu1, cpu2]), ("mobo", [mobo1]))
    """
    categorias = [categoria_info(key) for key, _ in productos_por_key]
    productos: dict[int, list[ProductoCatalogo]] = {
        CATEGORIA_ID[key]: lista for key, lista in productos_por_key
    }
    return CatalogoStub(categorias, productos)


@pytest.fixture
def categorias_todas() -> list[CategoriaInfo]:
    """Las 9 categorias reales de settings.categorias, con los IDs de prueba."""
    return [categoria_info(key) for key in CATEGORIA_ID]
