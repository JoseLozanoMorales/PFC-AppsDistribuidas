"""
Puerto de dominio para consultar el catalogo de productos. Segregado a
proposito: solo declara lo que recomendador.py realmente llama
(listar_categorias, listar_por_categoria). No incluye obtener_por_id porque
ese metodo lo usa armado_service.py directamente sobre producto_client, fuera
de este puerto -- anadirlo aqui violaria Interface Segregation sin necesidad.

producto_client (app/clients/producto_client.py) cumple este Protocol de
forma estructural, sin heredar de el ni importarlo: cualquier objeto con
estos dos metodos con esta firma sirve como implementacion valida.
"""
from typing import Protocol

from app.domain.models import CategoriaInfo, ProductoCatalogo


class CatalogoProvider(Protocol):
    def listar_categorias(self) -> list[CategoriaInfo]: ...

    def listar_por_categoria(self, categoria_id: int) -> list[ProductoCatalogo]: ...
