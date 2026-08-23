"""
Busqueda determinista de una configuracion alternativa bajo un presupuesto.
NUNCA la elige el LLM. Puerto 1:1 de RecomendadorService.java.

FUNCION OBJETIVO: maximizar min(score_cpu, score_gpu) -- el "techo" real de
rendimiento, ya que el componente mas debil es el que limita el desempeno
conjunto -- sujeto al presupuesto. NO se minimiza el porcentaje de bottleneck
directamente: hacerlo lleva a soluciones degeneradas (una CPU floja con una
GPU floja da 0% de desbalance pero rinde menos que la config original).

RESTRICCION: la recomendacion nunca ofrece una CPU+GPU con
min(score_cpu, score_gpu) menor al de la configuracion original del usuario.
Si no hay ninguna combinacion dentro del presupuesto que iguale o mejore ese
piso, no se propone alternativa de CPU/GPU -- se dice explicitamente por que.

Dos pasadas de asignacion de presupuesto:
  1) Cada categoria recibe un sub-presupuesto nominal via
     categoria_producto.peso_presupuesto (espejado en settings.categorias).
     CPU y Motherboard se resuelven como PAR (comparten presupuesto, socket
     compatible es restriccion dura).
  2) Lo que sobra se redistribuye entre categorias pendientes, en orden de
     peso descendente, hasta agotarse o cubrirlas todas.

No se hace una tercera pasada ni se re-optimizan categorias YA cubiertas con
el sobrante restante (ej. mejorar la GPU elegida si algo queda sin usar) --
deliberadamente fuera de este alcance.
"""
from dataclasses import dataclass, field
from decimal import Decimal

from app.config import settings
from app.domain import bottleneck
from app.domain.catalogo_provider import CatalogoProvider
from app.domain.models import ProductoCatalogo


@dataclass
class ResultadoRecomendacion:
    presupuesto_usado: Decimal
    componentes: dict[str, ProductoCatalogo]
    porcentaje_cuello_botella: float | None
    nivel_cuello_botella: str | None
    componente_limitante: str | None
    advertencias: list[str]


@dataclass(frozen=True)
class _OpcionCpu:
    cpu: ProductoCatalogo
    mobo: ProductoCatalogo
    precio_combinado: Decimal
    score: float


@dataclass(frozen=True)
class _OpcionGpu:
    gpu: ProductoCatalogo
    score: float


def _redondear1(valor: float) -> float:
    import math
    return math.floor(valor * 10.0 + 0.5) / 10.0


def _sin_advertencias() -> list[str]:
    return []


def recomendar(presupuesto_maximo: Decimal, cpu_original: ProductoCatalogo,
                gpu_original: ProductoCatalogo | None, catalogo: CatalogoProvider) -> ResultadoRecomendacion:
    advertencias: list[str] = []
    descartable: list[str] = []

    score_cpu_original = bottleneck.score_cpu(cpu_original, descartable)
    requiere_gpu = gpu_original is not None
    score_gpu_original = bottleneck.score_gpu(gpu_original, descartable) if requiere_gpu else None
    piso_original = min(score_cpu_original, score_gpu_original) if requiere_gpu else score_cpu_original

    categoria_id_por_key = _resolver_categoria_ids(advertencias, catalogo)
    pesos = _normalizar_pesos(categoria_id_por_key, advertencias)
    sub_presupuestos = _sub_presupuestos(presupuesto_maximo, pesos)

    elegidos: dict[str, ProductoCatalogo] = {}

    # ---- Pasada 1 ----
    presupuesto_conjunto_cpu = (
        sub_presupuestos.get("cpu", Decimal(0))
        + sub_presupuestos.get("mobo", Decimal(0))
        + (sub_presupuestos.get("gpu", Decimal(0)) if requiere_gpu else Decimal(0))
    )
    _resolver_cpu_mobo_gpu(categoria_id_por_key, presupuesto_conjunto_cpu, piso_original, requiere_gpu, elegidos, catalogo)
    if not requiere_gpu:
        _resolver_gpu(categoria_id_por_key, sub_presupuestos.get("gpu"), elegidos, catalogo)
    _resolver_ram(categoria_id_por_key, sub_presupuestos.get("ram"), elegidos, catalogo)
    _resolver_almacenamiento(categoria_id_por_key, sub_presupuestos.get("storage"), elegidos, catalogo)
    _resolver_por_precio("case", categoria_id_por_key, sub_presupuestos.get("case"), elegidos, catalogo)
    _resolver_por_precio("cooling", categoria_id_por_key, sub_presupuestos.get("cooling"), elegidos, catalogo)
    _resolver_por_precio("periferico", categoria_id_por_key, sub_presupuestos.get("periferico"), elegidos, catalogo)
    _resolver_psu(categoria_id_por_key, sub_presupuestos.get("psu"), elegidos, advertencias, catalogo)

    # ---- Pasada 2: redistribuir el sobrante entre categorias pendientes ----
    pendientes: set[str] = {key for key in categoria_id_por_key if key not in elegidos}

    sobrante = presupuesto_maximo - _suma_precios(elegidos)
    if sobrante > 0 and pendientes:
        orden_por_peso = sorted(pendientes, key=lambda k: pesos.get(k, 0.0), reverse=True)
        for key in orden_por_peso:
            if sobrante <= 0 or key not in pendientes:
                continue
            if key in ("cpu", "mobo"):
                resuelto = _resolver_cpu_mobo_gpu(categoria_id_por_key, sobrante, piso_original, requiere_gpu, elegidos, catalogo)
            elif key == "gpu":
                if requiere_gpu:
                    resuelto = _resolver_cpu_mobo_gpu(categoria_id_por_key, sobrante, piso_original, True, elegidos, catalogo)
                else:
                    resuelto = _resolver_gpu(categoria_id_por_key, sobrante, elegidos, catalogo)
            elif key == "ram":
                resuelto = _resolver_ram(categoria_id_por_key, sobrante, elegidos, catalogo)
            elif key == "storage":
                resuelto = _resolver_almacenamiento(categoria_id_por_key, sobrante, elegidos, catalogo)
            elif key == "psu":
                resuelto = _resolver_psu(categoria_id_por_key, sobrante, elegidos, advertencias, catalogo)
            else:
                resuelto = _resolver_por_precio(key, categoria_id_por_key, sobrante, elegidos, catalogo)

            if resuelto:
                pendientes.discard("cpu")
                pendientes.discard("mobo")
                if requiere_gpu:
                    pendientes.discard("gpu")
                pendientes.discard(key)
                sobrante = presupuesto_maximo - _suma_precios(elegidos)

    # CPU (y GPU si corresponde) nunca se degradan: si tras las 2 pasadas
    # sigue sin resolverse, se dice explicitamente por que en vez de
    # proponer algo peor.
    if "cpu" not in elegidos:
        pendientes.discard("cpu")
        pendientes.discard("mobo")
        if requiere_gpu:
            pendientes.discard("gpu")
        advertencias.append(
            f"No se encontro una combinacion de CPU{' + GPU' if requiere_gpu else ''} dentro del presupuesto "
            f"que iguale o mejore el rendimiento actual (minimo entre CPU y GPU actual: "
            f"{_redondear1(piso_original)}). No se recomienda reemplazar estos componentes por unos mas "
            "debiles; se mantienen los actuales."
        )

    for key in pendientes:
        nombre_categoria = settings.categorias[key].nombre
        advertencias.append(
            f"No hay opcion de '{nombre_categoria}' dentro del presupuesto total disponible (ni siquiera "
            "redistribuyendo el sobrante de otras categorias); se omitio de la recomendacion."
        )

    total = _suma_precios(elegidos)

    porcentaje = nivel = limitante = None
    if elegidos.get("cpu") is not None:
        resultado = bottleneck.calcular(elegidos["cpu"], elegidos.get("gpu"))
        porcentaje = resultado.porcentaje
        nivel = resultado.nivel
        limitante = resultado.componente_limitante

    return ResultadoRecomendacion(total, elegidos, porcentaje, nivel, limitante, advertencias)


def _resolver_categoria_ids(advertencias: list[str], catalogo: CatalogoProvider) -> dict[str, int]:
    categorias_reales = catalogo.listar_categorias()
    resultado: dict[str, int] = {}
    for key, cfg in settings.categorias.items():
        encontrada = next((c for c in categorias_reales if c.nombre.lower() == cfg.nombre.lower()), None)
        if encontrada is not None:
            resultado[key] = encontrada.id
        else:
            advertencias.append(
                f"La categoria '{cfg.nombre}' ({key}) no existe todavia en productos-service: "
                "no se puede recomendar esa parte."
            )
    return resultado


def _normalizar_pesos(categoria_id_por_key: dict[str, int], advertencias: list[str]) -> dict[str, float]:
    suma = sum(settings.categorias[key].peso_presupuesto for key in categoria_id_por_key)
    if suma <= 0:
        advertencias.append("Los pesos de presupuesto configurados suman 0; no se puede repartir el presupuesto.")
        return {}
    return {key: settings.categorias[key].peso_presupuesto / suma for key in categoria_id_por_key}


def _sub_presupuestos(presupuesto_maximo: Decimal, pesos: dict[str, float]) -> dict[str, Decimal]:
    return {key: presupuesto_maximo * Decimal(str(peso)) for key, peso in pesos.items()}


def _candidatos_dentro_de_presupuesto(categoria_id: int | None, presupuesto: Decimal | None,
                                       catalogo: CatalogoProvider) -> list[ProductoCatalogo]:
    if categoria_id is None or presupuesto is None or presupuesto <= 0:
        return []
    return [
        p for p in catalogo.listar_por_categoria(categoria_id)
        if p.habilitado and p.precio <= presupuesto
    ]


# CPU y Motherboard van acopladas por socket: nunca se recomienda una
# combinacion que no arma fisicamente, aunque cada una individualmente
# fuera la de mejor score en su categoria.
#
# Complejidad: las motherboards se agrupan por socket ordenadas por precio
# UNA vez (O(M log M)); armar la lista de "opciones de CPU" (cada una con su
# motherboard compatible mas barata) es O(C log M). La busqueda conjunta
# CPU x GPU sobre esas opciones ya filtradas por presupuesto y compatibilidad
# es O(C' x G'), tipicamente docenas, no miles, incluso en un catalogo
# grande. Documentado como decision de alcance: el mismo indice de "maximo
# por precio acumulado" que resuelve la compatibilidad de socket en O(1) por
# CPU bajaria esto a O((C+G) log(C+G)) si hiciera falta.
def _resolver_cpu_mobo_gpu(categoria_id_por_key: dict[str, int], presupuesto_conjunto: Decimal,
                            piso_minimo: float, requiere_gpu: bool, elegidos: dict[str, ProductoCatalogo],
                            catalogo: CatalogoProvider) -> bool:
    if "cpu" in elegidos:
        return False
    if "cpu" not in categoria_id_por_key or "mobo" not in categoria_id_por_key:
        return False

    todos_cpu = _candidatos_dentro_de_presupuesto(categoria_id_por_key.get("cpu"), presupuesto_conjunto, catalogo)
    todos_mobo = _candidatos_dentro_de_presupuesto(categoria_id_por_key.get("mobo"), presupuesto_conjunto, catalogo)

    motherboards_por_socket: dict[str, list[ProductoCatalogo]] = {}
    for mobo in todos_mobo:
        socket = mobo.atributo_texto("socket")
        if socket:
            motherboards_por_socket.setdefault(socket.upper(), []).append(mobo)
    for lista in motherboards_por_socket.values():
        lista.sort(key=lambda p: p.precio)

    descartable: list[str] = []
    opciones_cpu: list[_OpcionCpu] = []
    for cpu in todos_cpu:
        socket = cpu.atributo_texto("socket")
        if not socket:
            continue
        compatibles = motherboards_por_socket.get(socket.upper())
        if not compatibles:
            continue
        mobo_mas_barata = compatibles[0]
        precio_combinado = cpu.precio + mobo_mas_barata.precio
        if precio_combinado > presupuesto_conjunto:
            continue
        opciones_cpu.append(_OpcionCpu(cpu, mobo_mas_barata, precio_combinado, bottleneck.score_cpu(cpu, descartable)))

    if not requiere_gpu:
        candidatas = [o for o in opciones_cpu if o.score >= piso_minimo]
        if not candidatas:
            return False
        mejor = max(candidatas, key=lambda o: o.score)
        elegidos["cpu"] = mejor.cpu
        elegidos["mobo"] = mejor.mobo
        return True

    todos_gpu = _candidatos_dentro_de_presupuesto(categoria_id_por_key.get("gpu"), presupuesto_conjunto, catalogo)
    opciones_gpu = [_OpcionGpu(gpu, bottleneck.score_gpu(gpu, descartable)) for gpu in todos_gpu]

    mejor_cpu: _OpcionCpu | None = None
    mejor_gpu: ProductoCatalogo | None = None
    mejor_min = float("-inf")
    mejor_suma = float("-inf")
    for opcion_cpu in opciones_cpu:
        restante = presupuesto_conjunto - opcion_cpu.precio_combinado
        for opcion_gpu in opciones_gpu:
            if opcion_gpu.gpu.precio > restante:
                continue
            min_actual = min(opcion_cpu.score, opcion_gpu.score)
            if min_actual < piso_minimo:
                continue
            suma_actual = opcion_cpu.score + opcion_gpu.score
            # Empate en el "techo" (min): se prefiere la pareja con mayor
            # suma total como criterio secundario, ya declarado.
            if min_actual > mejor_min or (min_actual == mejor_min and suma_actual > mejor_suma):
                mejor_min = min_actual
                mejor_suma = suma_actual
                mejor_cpu = opcion_cpu
                mejor_gpu = opcion_gpu.gpu

    if mejor_cpu is None:
        return False
    elegidos["cpu"] = mejor_cpu.cpu
    elegidos["mobo"] = mejor_cpu.mobo
    elegidos["gpu"] = mejor_gpu
    return True


def _resolver_gpu(categoria_id_por_key: dict[str, int], presupuesto: Decimal | None,
                   elegidos: dict[str, ProductoCatalogo], catalogo: CatalogoProvider) -> bool:
    if "gpu" in elegidos or "gpu" not in categoria_id_por_key or presupuesto is None:
        return False
    candidatos = _candidatos_dentro_de_presupuesto(categoria_id_por_key.get("gpu"), presupuesto, catalogo)
    if not candidatos:
        return False
    descartable: list[str] = []
    mejor = max(candidatos, key=lambda gpu: bottleneck.score_gpu(gpu, descartable))
    elegidos["gpu"] = mejor
    return True


def _resolver_ram(categoria_id_por_key: dict[str, int], presupuesto: Decimal | None,
                   elegidos: dict[str, ProductoCatalogo], catalogo: CatalogoProvider) -> bool:
    if "ram" in elegidos:
        return False
    candidatos = _candidatos_dentro_de_presupuesto(categoria_id_por_key.get("ram"), presupuesto, catalogo)
    return _seleccionar_mejor(
        candidatos, "ram", elegidos,
        lambda p: (p.atributo_numerico("capacidad_gb") or 0.0, p.atributo_numerico("velocidad_mhz") or 0.0),
    )


def _resolver_almacenamiento(categoria_id_por_key: dict[str, int], presupuesto: Decimal | None,
                              elegidos: dict[str, ProductoCatalogo], catalogo: CatalogoProvider) -> bool:
    if "storage" in elegidos:
        return False
    candidatos = _candidatos_dentro_de_presupuesto(categoria_id_por_key.get("storage"), presupuesto, catalogo)
    return _seleccionar_mejor(
        candidatos, "storage", elegidos,
        lambda p: (_rank_tipo_almacenamiento(p.atributo_texto("tipo") or ""), p.atributo_numerico("capacidad_gb") or 0.0),
    )


def _rank_tipo_almacenamiento(tipo: str) -> int:
    tipo = tipo.upper()
    if tipo == "NVME":
        return 2
    if tipo == "SSD":
        return 1
    return 0


# La PSU se resuelve DESPUES de CPU/GPU a proposito: valida watts contra el
# TDP combinado de lo que realmente se eligio, no de forma aislada.
def _resolver_psu(categoria_id_por_key: dict[str, int], presupuesto: Decimal | None,
                   elegidos: dict[str, ProductoCatalogo], advertencias: list[str],
                   catalogo: CatalogoProvider) -> bool:
    if "psu" in elegidos:
        return False
    candidatos = _candidatos_dentro_de_presupuesto(categoria_id_por_key.get("psu"), presupuesto, catalogo)
    if not candidatos:
        return False

    tdp_cpu = (elegidos["cpu"].atributo_numerico("tdp") or 0.0) if elegidos.get("cpu") else 0.0
    tdp_gpu = (elegidos["gpu"].atributo_numerico("tdp") or 0.0) if elegidos.get("gpu") else 0.0
    margen_recomendado = (tdp_cpu + tdp_gpu) * settings.psu.margen_recomendado

    suficientes = [p for p in candidatos if (p.atributo_numerico("potencia_watts") or 0.0) >= margen_recomendado]
    if suficientes:
        elegida = min(suficientes, key=lambda p: p.atributo_numerico("potencia_watts") or 0.0)
    else:
        elegida = max(candidatos, key=lambda p: p.atributo_numerico("potencia_watts") or 0.0)
        watts = elegida.atributo_numerico("potencia_watts") or 0.0
        # El presupuesto asignado a esta categoria no alcanzo para una PSU con
        # margen suficiente: se avisa, igual que se advertiria para la
        # configuracion del propio usuario (ver advertencias.py) -- no se oculta.
        advertencias.append(
            f"La PSU recomendada ({int(watts)}W) no alcanza el margen recomendado ({int(margen_recomendado)}W) "
            "para el TDP combinado de la CPU+GPU recomendadas; el presupuesto asignado a esta categoria no "
            "permitio una mejor."
        )
    elegidos["psu"] = elegida
    return True


# Case, cooling y perifericos no tienen un atributo tecnico fuerte para
# diferenciar rendimiento en este catalogo: se aprovecha el presupuesto
# disponible tomando la opcion mas cara que entre en el (a igualdad de datos,
# mas precio suele correlacionar con mejores materiales/calidad). Heuristica
# declarada, no una medicion real.
def _resolver_por_precio(categoria_key: str, categoria_id_por_key: dict[str, int], presupuesto: Decimal | None,
                          elegidos: dict[str, ProductoCatalogo], catalogo: CatalogoProvider) -> bool:
    if categoria_key in elegidos:
        return False
    candidatos = _candidatos_dentro_de_presupuesto(categoria_id_por_key.get(categoria_key), presupuesto, catalogo)
    return _seleccionar_mejor(candidatos, categoria_key, elegidos, lambda p: p.precio)


def _seleccionar_mejor(candidatos: list[ProductoCatalogo], categoria_key: str,
                        elegidos: dict[str, ProductoCatalogo], comparador) -> bool:
    if not candidatos:
        return False
    elegidos[categoria_key] = max(candidatos, key=comparador)
    return True


def _suma_precios(elegidos: dict[str, ProductoCatalogo]) -> Decimal:
    total = Decimal(0)
    for producto in elegidos.values():
        total += producto.precio
    return total
