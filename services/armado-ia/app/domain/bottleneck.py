"""
Calculo 100% deterministico del cuello de botella: nunca se le pide este
numero a un LLM. Alcance CPU-vs-GPU unicamente; RAM/almacenamiento/PSU/socket
se resuelven aparte como advertencias (ver advertencias.py), no se mezclan en
el mismo score.

Normalizacion contra techos FIJOS (settings.cpu.*_max / settings.gpu.*_max),
no contra el min/max del catalogo actual: el mismo build debe dar siempre el
mismo porcentaje, sin importar que productos se agreguen o quiten del
catalogo despues.

Puerto 1:1 de BottleneckCalculator.java -- misma formula, mismos pesos.
"""
import math
from dataclasses import dataclass

from app.config import settings
from app.domain.models import ProductoCatalogo


@dataclass(frozen=True)
class _FactorPeso:
    nombre_atributo: str
    valor: float | None
    techo: float
    peso: float


@dataclass(frozen=True)
class ResultadoBottleneck:
    porcentaje: float
    nivel: str
    componente_limitante: str
    score_cpu: float
    score_gpu: float | None
    advertencias: list[str]


def _redondear1(valor: float) -> float:
    # Equivalente a Math.round(valor * 10.0) / 10.0 en Java (redondeo hacia
    # arriba en el punto medio), no al round() de Python (banker's rounding).
    return math.floor(valor * 10.0 + 0.5) / 10.0


def _calcular_score(factores: list[_FactorPeso], advertencias: list[str], etiqueta_componente: str) -> float:
    suma_pesos = 0.0
    suma_ponderada = 0.0
    for factor in factores:
        if factor.valor is not None:
            normalizado = min(1.0, max(0.0, factor.valor) / factor.techo)
            suma_ponderada += normalizado * factor.peso
            suma_pesos += factor.peso
        else:
            advertencias.append(
                f"Falta el atributo '{factor.nombre_atributo}' en {etiqueta_componente}: el score se calculo "
                "sin ese factor (peso redistribuido entre los demas)."
            )
    if suma_pesos == 0:
        advertencias.append(
            f"No hay atributos tecnicos suficientes para {etiqueta_componente}; no se puede estimar su "
            "rendimiento (score = 0)."
        )
        return 0.0
    return 100.0 * (suma_ponderada / suma_pesos)


def score_cpu(cpu: ProductoCatalogo, advertencias: list[str]) -> float:
    cfg = settings.cpu
    etiqueta = f"la CPU ({cpu.nombre})"
    factores = [
        _FactorPeso("nucleos", cpu.atributo_numerico("nucleos"), cfg.nucleos_max, cfg.peso_nucleos),
        _FactorPeso("frecuencia_turbo_ghz", cpu.atributo_numerico("frecuencia_turbo_ghz"),
                    cfg.frecuencia_turbo_ghz_max, cfg.peso_frecuencia),
        _FactorPeso("hilos", cpu.atributo_numerico("hilos"), cfg.hilos_max, cfg.peso_hilos),
    ]
    return _calcular_score(factores, advertencias, etiqueta)


def score_gpu(gpu: ProductoCatalogo, advertencias: list[str]) -> float:
    cfg = settings.gpu
    etiqueta = f"la GPU ({gpu.nombre})"
    factores = [
        _FactorPeso("nucleos", gpu.atributo_numerico("nucleos"), cfg.nucleos_max, cfg.peso_nucleos),
        _FactorPeso("vram_gb", gpu.atributo_numerico("vram_gb"), cfg.vram_gb_max, cfg.peso_vram),
        _FactorPeso("tdp", gpu.atributo_numerico("tdp"), cfg.tdp_max, cfg.peso_tdp),
    ]
    return _calcular_score(factores, advertencias, etiqueta)


def _nivel(porcentaje: float) -> str:
    if porcentaje >= settings.nivel.severo_desde:
        return "SEVERO"
    if porcentaje >= settings.nivel.moderado_desde:
        return "MODERADO"
    return "EQUILIBRADO"


def calcular(cpu: ProductoCatalogo, gpu: ProductoCatalogo | None) -> ResultadoBottleneck:
    advertencias: list[str] = []
    sc = score_cpu(cpu, advertencias)

    if gpu is None:
        return ResultadoBottleneck(0.0, "N/A", "N/A (sin GPU dedicada)", sc, None, advertencias)

    sg = score_gpu(gpu, advertencias)
    maximo = max(sc, sg)
    minimo = min(sc, sg)
    porcentaje = 0.0 if maximo <= 0 else _redondear1((maximo - minimo) / maximo * 100)

    if sc < sg:
        limitante = "CPU"
    elif sg < sc:
        limitante = "GPU"
    else:
        limitante = "NINGUNO (equilibrado)"

    return ResultadoBottleneck(porcentaje, _nivel(porcentaje), limitante, sc, sg, advertencias)
