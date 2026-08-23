"""
Tests de app/domain/bottleneck.py: calculo 100% deterministico del cuello de
botella CPU-vs-GPU. Normaliza contra techos fijos (settings.cpu.*_max /
settings.gpu.*_max), asi que el mismo build siempre debe dar el mismo
porcentaje.
"""
from app.domain import bottleneck
from tests.conftest import producto


def test_calcular_i5_13400f_rtx4070ti_34_8_por_ciento_limitado_por_cpu():
    """
    Caso real verificado a mano, con los mismos atributos que
    docs/seed-catalogo.sql usa para estos dos productos:
      i5-13400F:   nucleos=10, hilos=16, frecuencia_turbo_ghz=4.6
      RTX 4070 Ti: nucleos=7680, vram_gb=12, tdp=285

    score_cpu = 100*(0.4*10/24 + 0.4*4.6/6.0 + 0.2*16/48)      = 54.0
    score_gpu = 100*(0.55*7680/9000 + 0.30*12/16 + 0.15*285/320) = 82.79...
    porcentaje = (82.79... - 54.0) / 82.79... * 100 -> redondeado = 34.8
    """
    cpu = producto(1, "cpu", 220, nombre="Intel Core i5-13400F", socket="LGA1700",
                    nucleos=10, hilos=16, frecuencia_turbo_ghz=4.6, tdp=65)
    gpu = producto(2, "gpu", 780, nombre="NVIDIA RTX 4070 Ti",
                    nucleos=7680, vram_gb=12, tdp=285)

    resultado = bottleneck.calcular(cpu, gpu)

    assert resultado.score_cpu == 54.0
    assert resultado.porcentaje == 34.8
    assert resultado.componente_limitante == "CPU"
    assert resultado.nivel == "MODERADO"  # 20 <= 34.8 < 50
    assert resultado.advertencias == []


def test_calcular_sin_gpu_dedicada():
    cpu = producto(1, "cpu", 200, nucleos=8, frecuencia_turbo_ghz=3.5, hilos=16)

    resultado = bottleneck.calcular(cpu, None)

    assert resultado.porcentaje == 0.0
    assert resultado.nivel == "N/A"
    assert resultado.componente_limitante == "N/A (sin GPU dedicada)"
    assert resultado.score_gpu is None


def test_calcular_scores_iguales_es_equilibrado_sin_limitante():
    # Mismos ratios normalizados (r=0.5) en CPU y GPU -> score_cpu == score_gpu.
    cpu = producto(1, "cpu", 200, nucleos=12, frecuencia_turbo_ghz=3.0, hilos=24)
    gpu = producto(2, "gpu", 300, nucleos=4500, vram_gb=8, tdp=160)

    resultado = bottleneck.calcular(cpu, gpu)

    assert resultado.score_cpu == resultado.score_gpu == 50.0
    assert resultado.porcentaje == 0.0
    assert resultado.componente_limitante == "NINGUNO (equilibrado)"
    assert resultado.nivel == "EQUILIBRADO"


def test_calcular_gpu_como_limitante_cuando_cpu_es_mas_fuerte():
    cpu = producto(1, "cpu", 500, nucleos=24, frecuencia_turbo_ghz=6.0, hilos=48)  # score=100.0 (techo)
    gpu = producto(2, "gpu", 100, nucleos=900, vram_gb=1.6, tdp=32)  # score=10.0

    resultado = bottleneck.calcular(cpu, gpu)

    assert resultado.score_cpu > resultado.score_gpu
    assert resultado.componente_limitante == "GPU"
    assert resultado.nivel == "SEVERO"


def test_calcular_desbalance_severo_gpu_muy_superior_a_cpu():
    cpu = producto(1, "cpu", 100, nucleos=4, frecuencia_turbo_ghz=1.2, hilos=8)  # score bajo
    gpu = producto(2, "gpu", 900, nucleos=9000, vram_gb=16, tdp=320)  # score=100.0 (techo)

    resultado = bottleneck.calcular(cpu, gpu)

    assert resultado.componente_limitante == "CPU"
    assert resultado.nivel == "SEVERO"
    assert resultado.porcentaje >= 50


def test_score_cpu_con_atributo_faltante_redistribuye_el_peso_y_advierte():
    # Sin 'hilos': el score se calcula solo con nucleos+frecuencia, peso
    # redistribuido entre esos dos factores (0.4/0.8 cada uno = 0.5 cada uno).
    cpu = producto(1, "cpu", 200, nucleos=12, frecuencia_turbo_ghz=3.0)
    advertencias: list[str] = []

    score = bottleneck.score_cpu(cpu, advertencias)

    # nucleos=12/24=0.5, frecuencia=3.0/6.0=0.5 -> promedio ponderado = 0.5 -> 50.0
    assert score == 50.0
    assert any("hilos" in a and "la CPU" in a for a in advertencias), advertencias


def test_score_gpu_sin_ningun_atributo_tecnico_da_score_cero_y_advierte():
    gpu = producto(1, "gpu", 200, nombre="GPU sin ficha tecnica")
    advertencias: list[str] = []

    score = bottleneck.score_gpu(gpu, advertencias)

    assert score == 0.0
    assert any("No hay atributos tecnicos suficientes" in a for a in advertencias), advertencias


def test_nivel_limites_moderado_y_severo():
    # moderado_desde=20, severo_desde=50 (ver settings.nivel). Se prueban los
    # limites exactos construyendo CPU/GPU cuyo porcentaje cae justo en cada
    # frontera, no solo casos comodos en medio de cada rango.
    # score_cpu=80.0 (r=0.8), score_gpu=100.0 (techo) -> porcentaje=20.0 exacto.
    cpu_moderado = producto(1, "cpu", 200, nucleos=19.2, frecuencia_turbo_ghz=4.8, hilos=38.4)
    gpu_techo = producto(2, "gpu", 900, nucleos=9000, vram_gb=16, tdp=320)
    resultado_moderado = bottleneck.calcular(cpu_moderado, gpu_techo)
    assert resultado_moderado.porcentaje == 20.0
    assert resultado_moderado.nivel == "MODERADO"

    # score_cpu=50.0 (r=0.5), score_gpu=100.0 (techo) -> porcentaje=50.0 exacto.
    cpu_severo = producto(3, "cpu", 200, nucleos=12, frecuencia_turbo_ghz=3.0, hilos=24)
    resultado_severo = bottleneck.calcular(cpu_severo, gpu_techo)
    assert resultado_severo.porcentaje == 50.0
    assert resultado_severo.nivel == "SEVERO"
