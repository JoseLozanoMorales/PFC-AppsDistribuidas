"""
Tests de app/domain/recomendador.py, el modulo con mas logica de negocio del
servicio (busqueda de la configuracion alternativa bajo presupuesto).

Testeable sin red: recomendar() recibe un CatalogoProvider inyectado (ver
app/domain/catalogo_provider.py), asi que estos tests usan CatalogoStub (ver
conftest.py) en vez de mockear HTTP contra productos-service.
"""
from decimal import Decimal

import pytest

from app.domain import recomendador
from tests.conftest import CatalogoStub, catalogo_de, producto

# --------------------------------------------------------------------------
# 1) FUNCION OBJETIVO: maximizar min(score_cpu, score_gpu), no minimizar el
#    desbalance.
# --------------------------------------------------------------------------


def test_recomendar_maximiza_el_minimo_no_minimiza_el_desbalance():
    """
    Catalogo con 2 CPU x 2 GPU, mismo socket para las 2 CPU (la
    compatibilidad de socket se prueba aparte, aqui no debe ser el factor
    decisivo):

      CPU debil (score=25.0)   + GPU debil (score=25.0)   -> min=25, desbalance=0%   (EQUILIBRADO)
      CPU fuerte (score=75.0)  + GPU fuerte (score=85.0)  -> min=75, desbalance=11.8% (no equilibrado)

    Un algoritmo que minimizara el desbalance elegiria debil+debil (0% de
    diferencia). El correcto, que maximiza el "techo" real de rendimiento,
    debe elegir fuerte+fuerte a pesar de tener mas desbalance porcentual,
    porque su minimo (75) es mayor que el de cualquier otra combinacion.
    """
    mobo = producto(201, "mobo", 80, nombre="Mobo AM5", socket="AM5")
    cpu_debil = producto(101, "cpu", 150, nombre="CPU debil", socket="AM5",
                          nucleos=6, frecuencia_turbo_ghz=1.5, hilos=12)
    cpu_fuerte = producto(102, "cpu", 300, nombre="CPU fuerte", socket="AM5",
                           nucleos=18, frecuencia_turbo_ghz=4.5, hilos=36)
    gpu_debil = producto(301, "gpu", 150, nombre="GPU debil",
                          nucleos=2250, vram_gb=4, tdp=80)
    gpu_fuerte = producto(302, "gpu", 400, nombre="GPU fuerte",
                           nucleos=7650, vram_gb=13.6, tdp=272)

    catalogo = catalogo_de(
        ("cpu", [cpu_debil, cpu_fuerte]),
        ("mobo", [mobo]),
        ("gpu", [gpu_debil, gpu_fuerte]),
    )

    # Original muy debil: cualquier combinacion del catalogo lo supera, asi
    # que la restriccion de no-degradacion (caso 2) no interfiere aqui.
    cpu_original = producto(1, "cpu", 50, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)
    gpu_original = producto(2, "gpu", 50, nucleos=100, vram_gb=1, tdp=20)

    resultado = recomendador.recomendar(Decimal(3000), cpu_original, gpu_original, catalogo)

    assert resultado.componentes["cpu"].id == cpu_fuerte.id
    assert resultado.componentes["gpu"].id == gpu_fuerte.id
    # Si hubiera minimizado el desbalance habria elegido debil+debil (0%).
    # 11.8% > 0 prueba que no fue ese el criterio.
    assert resultado.porcentaje_cuello_botella == 11.8
    assert resultado.componente_limitante == "CPU"


# --------------------------------------------------------------------------
# 2) RESTRICCION DE NO DEGRADACION
# --------------------------------------------------------------------------


def test_recomendar_no_degrada_si_nada_supera_el_piso_original():
    """
    cpu_original/gpu_original ya tienen el score maximo posible (100.0). Todo
    lo que ofrece el catalogo es mas debil. El recomendador NO debe proponer
    ningun cambio de CPU/GPU y debe explicar por que en advertencias.
    """
    mobo = producto(201, "mobo", 80, nombre="Mobo AM5", socket="AM5")
    cpu_mas_debil = producto(101, "cpu", 300, nombre="CPU inferior", socket="AM5",
                              nucleos=18, frecuencia_turbo_ghz=4.5, hilos=36)  # score=75.0
    gpu_mas_debil = producto(301, "gpu", 400, nombre="GPU inferior",
                              nucleos=7650, vram_gb=13.6, tdp=272)  # score=85.0

    catalogo = catalogo_de(
        ("cpu", [cpu_mas_debil]),
        ("mobo", [mobo]),
        ("gpu", [gpu_mas_debil]),
    )

    cpu_original = producto(1, "cpu", 999, nucleos=24, frecuencia_turbo_ghz=6.0, hilos=48)  # score=100.0
    gpu_original = producto(2, "gpu", 999, nucleos=9000, vram_gb=16, tdp=320)  # score=100.0

    resultado = recomendador.recomendar(Decimal(3000), cpu_original, gpu_original, catalogo)

    assert "cpu" not in resultado.componentes
    assert "mobo" not in resultado.componentes
    assert "gpu" not in resultado.componentes
    assert resultado.porcentaje_cuello_botella is None

    mensaje = "No se encontro una combinacion de CPU + GPU dentro del presupuesto"
    assert any(mensaje in a for a in resultado.advertencias), resultado.advertencias
    assert any("100.0" in a for a in resultado.advertencias), resultado.advertencias


# --------------------------------------------------------------------------
# 3) COMPATIBILIDAD DE SOCKET
# --------------------------------------------------------------------------


def test_recomendar_nunca_combina_socket_incompatible():
    """
    CPU-A tiene el score individual mas alto (100.0, socket LGA1700) pero
    NINGUNA motherboard del catalogo tiene ese socket. CPU-B tiene un score
    mucho menor (35.0) pero SI hay una motherboard compatible (AM5).

    El recomendador debe elegir CPU-B, nunca CPU-A emparejada con la unica
    motherboard disponible (que no arma fisicamente).
    """
    cpu_a_incompatible = producto(101, "cpu", 300, nombre="CPU-A (mejor score, sin mobo compatible)",
                                   socket="LGA1700", nucleos=24, frecuencia_turbo_ghz=6.0, hilos=48)
    cpu_b_compatible = producto(102, "cpu", 150, nombre="CPU-B (peor score, con mobo compatible)",
                                 socket="AM5", nucleos=6, frecuencia_turbo_ghz=3.0, hilos=12)
    mobo_am5 = producto(201, "mobo", 80, nombre="Mobo AM5", socket="AM5")

    catalogo = catalogo_de(
        ("cpu", [cpu_a_incompatible, cpu_b_compatible]),
        ("mobo", [mobo_am5]),
    )

    cpu_original = producto(1, "cpu", 50, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)

    resultado = recomendador.recomendar(Decimal(2000), cpu_original, None, catalogo)

    assert resultado.componentes["cpu"].id == cpu_b_compatible.id
    assert resultado.componentes["mobo"].id == mobo_am5.id
    assert (resultado.componentes["cpu"].atributo_texto("socket")
            == resultado.componentes["mobo"].atributo_texto("socket"))


# --------------------------------------------------------------------------
# 4) REDISTRIBUCION del sobrante en dos pasadas
# --------------------------------------------------------------------------


def test_recomendar_redistribuye_sobrante_y_cubre_las_9_categorias():
    """
    cooling y periferico (1% del presupuesto cada uno, ~$30 con $3000 de
    presupuesto) cuestan mas que su asignacion nominal ($50 y $40) -- en la
    pasada 1 quedan pendientes. El resto de categorias se compran muy por
    debajo de su presupuesto nominal, dejando sobrante de sobra. La pasada 2
    debe redistribuir ese sobrante y terminar cubriendo las 9 categorias.
    """
    cpu = producto(101, "cpu", 200, socket="AM5", nucleos=10, frecuencia_turbo_ghz=4.0, hilos=16, tdp=65)
    mobo = producto(201, "mobo", 80, socket="AM5")
    gpu = producto(301, "gpu", 300, nucleos=5000, vram_gb=8, tdp=200)
    ram = producto(401, "ram", 50, capacidad_gb=16, velocidad_mhz=3200)
    storage = producto(501, "storage", 60, tipo="NVME", capacidad_gb=1000)
    psu = producto(601, "psu", 50, potencia_watts=550)
    case_ = producto(701, "case", 70)
    cooling = producto(801, "cooling", 50)       # > $30 nominal (1% de 3000)
    periferico = producto(901, "periferico", 40)  # > $30 nominal (1% de 3000)

    catalogo = catalogo_de(
        ("cpu", [cpu]), ("mobo", [mobo]), ("gpu", [gpu]), ("ram", [ram]),
        ("storage", [storage]), ("psu", [psu]), ("case", [case_]),
        ("cooling", [cooling]), ("periferico", [periferico]),
    )

    cpu_original = producto(1, "cpu", 50, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)
    gpu_original = producto(2, "gpu", 50, nucleos=100, vram_gb=1, tdp=20)

    resultado = recomendador.recomendar(Decimal(3000), cpu_original, gpu_original, catalogo)

    assert set(resultado.componentes.keys()) == {
        "cpu", "mobo", "ram", "storage", "gpu", "psu", "case", "cooling", "periferico",
    }
    assert resultado.componentes["cooling"].id == cooling.id
    assert resultado.componentes["periferico"].id == periferico.id
    assert resultado.presupuesto_usado == Decimal(900)
    assert not any("no hay opcion" in a.lower() for a in resultado.advertencias), resultado.advertencias


# --------------------------------------------------------------------------
# 5) CATALOGO VACIO / SIN CANDIDATOS EN UNA CATEGORIA -> advertencia
#    explicita, nunca omision silenciosa.
# --------------------------------------------------------------------------


def test_recomendar_catalogo_completamente_vacio_no_falla_y_advierte():
    catalogo = CatalogoStub(categorias=[], productos={})
    cpu_original = producto(1, "cpu", 50, nucleos=4, frecuencia_turbo_ghz=2.0, hilos=8)

    resultado = recomendador.recomendar(Decimal(1000), cpu_original, None, catalogo)

    assert resultado.componentes == {}
    assert resultado.porcentaje_cuello_botella is None
    assert any("no existe todavia" in a and "Procesador" in a for a in resultado.advertencias), \
        resultado.advertencias
    assert any("pesos de presupuesto configurados suman 0" in a for a in resultado.advertencias), \
        resultado.advertencias
    assert any("No se encontro una combinacion de CPU dentro del presupuesto" in a
               for a in resultado.advertencias), resultado.advertencias


def test_recomendar_categoria_sin_candidatos_advierte_en_vez_de_omitir_en_silencio():
    """
    'storage' existe como categoria (productos-service la conoce) pero no
    tiene ningun producto habilitado en el catalogo. cpu/mobo si se resuelven
    normalmente -- la categoria vacia no debe bloquear al resto.
    """
    cpu = producto(101, "cpu", 150, socket="AM5", nucleos=6, frecuencia_turbo_ghz=3.0, hilos=12)
    mobo = producto(201, "mobo", 80, socket="AM5")

    catalogo = catalogo_de(
        ("cpu", [cpu]),
        ("mobo", [mobo]),
        ("storage", []),  # categoria conocida, cero productos
    )

    cpu_original = producto(1, "cpu", 50, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)

    resultado = recomendador.recomendar(Decimal(2000), cpu_original, None, catalogo)

    assert resultado.componentes["cpu"].id == cpu.id
    assert "storage" not in resultado.componentes
    mensaje = (
        "No hay opcion de 'Almacenamiento' dentro del presupuesto total disponible "
        "(ni siquiera redistribuyendo el sobrante de otras categorias); se omitio "
        "de la recomendacion."
    )
    assert mensaje in resultado.advertencias


# --------------------------------------------------------------------------
# 6) CASOS BORDE
# --------------------------------------------------------------------------


def test_recomendar_build_sin_gpu_no_agrega_gpu_si_la_categoria_no_existe():
    """gpu_original=None (graficos integrados) y el catalogo ni siquiera
    ofrece la categoria GPU: el resultado no debe traer 'gpu', y el bottleneck
    debe caer en el camino 'sin GPU dedicada' de bottleneck.calcular()."""
    cpu = producto(101, "cpu", 150, socket="AM5", nucleos=6, frecuencia_turbo_ghz=3.0, hilos=12)
    mobo = producto(201, "mobo", 80, socket="AM5")
    catalogo = catalogo_de(("cpu", [cpu]), ("mobo", [mobo]))

    cpu_original = producto(1, "cpu", 50, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)

    resultado = recomendador.recomendar(Decimal(2000), cpu_original, None, catalogo)

    assert resultado.componentes["cpu"].id == cpu.id
    assert "gpu" not in resultado.componentes
    assert resultado.porcentaje_cuello_botella == 0.0
    assert resultado.nivel_cuello_botella == "N/A"


def test_recomendar_build_sin_gpu_agrega_gpu_si_hay_categoria_disponible():
    """gpu_original=None pero SI hay categoria GPU con stock: el recomendador
    trata la GPU como mejora opcional disponible y la agrega."""
    cpu = producto(101, "cpu", 150, socket="AM5", nucleos=6, frecuencia_turbo_ghz=3.0, hilos=12)
    mobo = producto(201, "mobo", 80, socket="AM5")
    gpu = producto(301, "gpu", 200, nucleos=5000, vram_gb=8, tdp=200)
    catalogo = catalogo_de(("cpu", [cpu]), ("mobo", [mobo]), ("gpu", [gpu]))

    cpu_original = producto(1, "cpu", 50, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)

    resultado = recomendador.recomendar(Decimal(2000), cpu_original, None, catalogo)

    assert resultado.componentes["gpu"].id == gpu.id


def test_recomendar_presupuesto_muy_bajo_no_falla_y_no_degrada():
    catalogo = catalogo_de(
        ("cpu", [producto(101, "cpu", 150, socket="AM5", nucleos=6, frecuencia_turbo_ghz=3.0, hilos=12)]),
        ("mobo", [producto(201, "mobo", 80, socket="AM5")]),
    )
    cpu_original = producto(1, "cpu", 50, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)

    resultado = recomendador.recomendar(Decimal(1), cpu_original, None, catalogo)

    assert "cpu" not in resultado.componentes
    assert resultado.presupuesto_usado <= Decimal(1)
    assert len(resultado.advertencias) > 0


def test_recomendar_presupuesto_cero_no_falla_y_no_compra_nada():
    catalogo = catalogo_de(
        ("cpu", [producto(101, "cpu", 150, socket="AM5", nucleos=6, frecuencia_turbo_ghz=3.0, hilos=12)]),
        ("mobo", [producto(201, "mobo", 80, socket="AM5")]),
    )
    cpu_original = producto(1, "cpu", 50, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)

    resultado = recomendador.recomendar(Decimal(0), cpu_original, None, catalogo)

    assert resultado.componentes == {}
    assert resultado.presupuesto_usado == Decimal(0)


def test_recomendar_pasada2_rescata_cpu_y_mobo_que_no_cupieron_en_pasada1():
    """
    El presupuesto conjunto NOMINAL de cpu+mobo no alcanza para la unica
    combinacion disponible ($60+$30=$90 > conjunto nominal de $80.77 con
    presupuesto total $100 y solo 3 categorias activas). RAM se compra muy
    por debajo de su nominal, dejando sobrante suficiente para que la pasada
    2 rescate a cpu+mobo -- no solo categorias de bajo peso como cooling.
    """
    cpu = producto(101, "cpu", 60, socket="AM5", nucleos=6, frecuencia_turbo_ghz=3.0, hilos=12)
    mobo = producto(201, "mobo", 30, socket="AM5")
    ram = producto(401, "ram", 5, capacidad_gb=8, velocidad_mhz=2400)

    catalogo = catalogo_de(("cpu", [cpu]), ("mobo", [mobo]), ("ram", [ram]))
    cpu_original = producto(1, "cpu", 10, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)

    resultado = recomendador.recomendar(Decimal(100), cpu_original, None, catalogo)

    assert resultado.componentes["cpu"].id == cpu.id
    assert resultado.componentes["mobo"].id == mobo.id
    assert resultado.componentes["ram"].id == ram.id


def test_recomendar_psu_insuficiente_advierte_margen_en_vez_de_ocultarlo():
    """Si ninguna PSU del presupuesto alcanza el margen recomendado sobre el
    TDP combinado, igual se elige la de mayor potencia disponible, pero con
    advertencia explicita -- nunca en silencio."""
    cpu = producto(101, "cpu", 150, socket="AM5", nucleos=6, frecuencia_turbo_ghz=3.0, hilos=12, tdp=65)
    mobo = producto(201, "mobo", 80, socket="AM5")
    gpu = producto(301, "gpu", 300, nucleos=5000, vram_gb=8, tdp=250)  # TDP combinado=315W, margen=1.7x=535.5W
    psu_insuficiente = producto(601, "psu", 50, potencia_watts=400)  # muy por debajo de 535.5W

    catalogo = catalogo_de(
        ("cpu", [cpu]), ("mobo", [mobo]), ("gpu", [gpu]), ("psu", [psu_insuficiente]),
    )
    cpu_original = producto(1, "cpu", 10, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)
    gpu_original = producto(2, "gpu", 10, nucleos=100, vram_gb=1, tdp=20)

    resultado = recomendador.recomendar(Decimal(2000), cpu_original, gpu_original, catalogo)

    assert resultado.componentes["psu"].id == psu_insuficiente.id
    assert any("no alcanza el margen recomendado" in a for a in resultado.advertencias), resultado.advertencias


def test_recomendar_almacenamiento_prefiere_nvme_sobre_ssd_sobre_hdd_a_igual_capacidad():
    hdd = producto(501, "storage", 40, nombre="HDD 1TB", tipo="HDD", capacidad_gb=1000)
    ssd = producto(502, "storage", 45, nombre="SSD 1TB", tipo="SSD", capacidad_gb=1000)
    nvme = producto(503, "storage", 50, nombre="NVMe 1TB", tipo="NVME", capacidad_gb=1000)

    catalogo = catalogo_de(("storage", [hdd, ssd, nvme]))
    cpu_original = producto(1, "cpu", 10, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)

    resultado = recomendador.recomendar(Decimal(1000), cpu_original, None, catalogo)

    assert resultado.componentes["storage"].id == nvme.id


def test_recomendar_presupuesto_none_no_es_responsabilidad_de_recomendador():
    """
    recomendar() esta tipado para recibir Decimal, no Decimal | None. El
    filtro de "el usuario no pidio recomendacion" vive una capa arriba, en
    armado_service.analizar() (pide_recomendacion = presupuesto_maximo is not
    None), ANTES de llegar aqui. Este test documenta ese contrato: pasar None
    a recomendar() no es un caso que este modulo maneje con gracia -- debe
    fallar ruidosamente (TypeError en la aritmetica de Decimal) en vez de
    devolver silenciosamente una recomendacion vacia que enmascare un bug de
    quien lo llama.
    """
    catalogo = catalogo_de(
        ("cpu", [producto(101, "cpu", 150, socket="AM5", nucleos=6, frecuencia_turbo_ghz=3.0, hilos=12)]),
    )
    cpu_original = producto(1, "cpu", 50, nucleos=1, frecuencia_turbo_ghz=0.5, hilos=2)

    with pytest.raises(TypeError):
        recomendador.recomendar(None, cpu_original, None, catalogo)
