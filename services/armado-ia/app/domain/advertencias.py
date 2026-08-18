"""
Reglas deterministas que NO forman parte del porcentaje de cuello de botella
(ver bottleneck.py) pero son igual de accionables: socket CPU/Motherboard,
capacidad de RAM, tipo de almacenamiento y watts de la fuente contra el TDP
combinado. Ninguna la decide el LLM. Puerto 1:1 de AdvertenciasTecnicas.java.
"""
from app.config import settings
from app.domain.models import ProductoCatalogo


def _fmt(valor: float) -> str:
    return str(int(valor)) if valor == int(valor) else str(valor)


def _chequear_socket(cpu: ProductoCatalogo | None, mobo: ProductoCatalogo | None, advertencias: list[str]) -> None:
    # Advertencia, NO bloquea el analisis (a diferencia de la busqueda de
    # recomendacion, que si trata esto como restriccion dura).
    if cpu is None or mobo is None:
        return
    socket_cpu = cpu.atributo_texto("socket")
    socket_mobo = mobo.atributo_texto("socket")
    if socket_cpu and socket_mobo and socket_cpu.upper() != socket_mobo.upper():
        advertencias.append(
            f"El socket de la CPU ({socket_cpu}) no coincide con el de la motherboard ({socket_mobo}): "
            "esta combinacion no arma fisicamente."
        )


def _chequear_ram(ram: ProductoCatalogo | None, advertencias: list[str]) -> None:
    if ram is None:
        return
    minimo = settings.ram.capacidad_minima_gb
    capacidad = ram.atributo_numerico("capacidad_gb")
    if capacidad is None:
        advertencias.append("No se cargo 'capacidad_gb' para la RAM: no se pudo evaluar si es suficiente.")
    elif capacidad < minimo:
        advertencias.append(f"La RAM ({_fmt(capacidad)}GB) esta por debajo del minimo recomendado ({_fmt(minimo)}GB).")


def _chequear_almacenamiento(storage: ProductoCatalogo | None, advertencias: list[str]) -> None:
    if storage is None:
        return
    tipo = storage.atributo_texto("tipo")
    if tipo and tipo.upper() == "HDD":
        advertencias.append(
            "El almacenamiento es HDD: puede generar cuellos de botella de carga "
            "(no afecta el porcentaje de computo CPU/GPU)."
        )


def _chequear_psu(psu: ProductoCatalogo | None, cpu: ProductoCatalogo | None, gpu: ProductoCatalogo | None,
                   advertencias: list[str]) -> None:
    if psu is None or cpu is None:
        return
    tdp_cpu = cpu.atributo_numerico("tdp") or 0.0
    tdp_gpu = (gpu.atributo_numerico("tdp") or 0.0) if gpu is not None else 0.0
    tdp_total = tdp_cpu + tdp_gpu
    if tdp_total <= 0:
        return
    margen_recomendado = tdp_total * settings.psu.margen_recomendado
    watts = psu.atributo_numerico("potencia_watts")
    if watts is None:
        advertencias.append("No se cargo 'potencia_watts' para la fuente: no se pudo validar contra el TDP combinado.")
    elif watts < margen_recomendado:
        advertencias.append(
            f"La fuente ({int(watts)}W) podria no alcanzar con margen el TDP combinado estimado "
            f"({int(tdp_total)}W; recomendado >= {int(margen_recomendado)}W)."
        )


def evaluar(componentes: dict[str, ProductoCatalogo]) -> list[str]:
    advertencias: list[str] = []
    _chequear_socket(componentes.get("cpu"), componentes.get("mobo"), advertencias)
    _chequear_ram(componentes.get("ram"), advertencias)
    _chequear_almacenamiento(componentes.get("storage"), advertencias)
    _chequear_psu(componentes.get("psu"), componentes.get("cpu"), componentes.get("gpu"), advertencias)
    return advertencias
