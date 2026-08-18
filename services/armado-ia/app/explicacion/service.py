"""
Envoltorio que garantiza que el LLM NUNCA sea un punto de fallo duro NI una
fuente de datos falsos: sin ExplicacionClient configurado, sin credenciales,
con cualquier error de red/modelo, O si el texto generado menciona un
modelo/spec que no vino en los datos de entrada (alucinacion), se cae a una
explicacion determinista construida solo con los datos ya calculados. Mejor
una explicacion sosa que una que miente.
"""
import logging
import re

from app.config import settings
from app.explicacion.client import ContextoExplicacion, ExplicacionClient

log = logging.getLogger("armado_ia.explicacion")

# Patrones de nombres de modelo de hardware que un LLM podria "recordar" de
# su entrenamiento y colar en el texto aunque no vengan en los datos. No es
# un detector perfecto de alucinaciones en general -- es un guardrail
# dirigido al caso real que se detecto (inventar GPU/CPU/PSU especificas).
_PATRONES_MODELO_HARDWARE = [
    re.compile(r"\bRTX\s?\d{3,4}(\s?Ti)?\b", re.IGNORECASE),
    re.compile(r"\bGTX\s?\d{3,4}(\s?Ti)?\b", re.IGNORECASE),
    re.compile(r"\bRX\s?\d{3,4}(\s?XT)?\b", re.IGNORECASE),
    re.compile(r"\bRyzen\s?\d\s?\d{3,4}[A-Z]*\b", re.IGNORECASE),
    re.compile(r"\bi[3579]-\d{4,5}[A-Z]*\b", re.IGNORECASE),
    re.compile(r"\b\d{3,4}\s?W\b"),
]


def _normalizar(texto: str) -> str:
    return re.sub(r"\s+", "", texto).upper()


class ExplicacionService:
    def __init__(self, cliente: ExplicacionClient | None):
        self._cliente = cliente

    def generar(self, contexto: ContextoExplicacion) -> str:
        if self._cliente is None:
            log.info("Sin ExplicacionClient activo (ver settings.explicacion.proveedor); usando fallback deterministico")
            return self._explicacion_fallback(contexto)
        try:
            texto = self._cliente.explicar(contexto)
            if not texto or not texto.strip():
                raise RuntimeError("Respuesta vacia del LLM")
            texto = texto.strip()
            token_no_verificado = self._token_no_verificado(texto, contexto)
            if token_no_verificado is not None:
                log.warning(
                    "Explicacion del LLM descartada: menciona '%s', que no aparece en los datos de entrada "
                    "(posible alucinacion). Usando fallback deterministico.", token_no_verificado
                )
                return self._explicacion_fallback(contexto)
            return texto
        except Exception as exc:  # noqa: BLE001 -- el LLM nunca debe tumbar la respuesta
            log.warning("Fallo la explicacion via LLM (%s), usando fallback deterministico", exc)
            return self._explicacion_fallback(contexto)

    def _token_no_verificado(self, texto: str, contexto: ContextoExplicacion) -> str | None:
        vocabulario = _normalizar(self._construir_vocabulario_permitido(contexto))
        for patron in _PATRONES_MODELO_HARDWARE:
            for match in patron.finditer(texto):
                token = _normalizar(match.group())
                if token not in vocabulario:
                    return match.group()
        return None

    @staticmethod
    def _construir_vocabulario_permitido(c: ContextoExplicacion) -> str:
        partes: list[str] = list(c.componentes_nombres.values())
        partes.extend(c.advertencias)
        r = c.recomendacion
        if r is not None:
            partes.extend(comp.nombre for comp in r.componentes.values())
            partes.extend(r.advertencias)
        return " ".join(partes)

    def _explicacion_fallback(self, c: ContextoExplicacion) -> str:
        partes: list[str] = []
        if c.nivel == "N/A":
            partes.append(
                "Esta configuracion no incluye una GPU dedicada, por lo que no aplica el calculo de "
                "cuello de botella CPU-GPU."
            )
        else:
            partes.append(
                f"Se detecto un cuello de botella del {c.porcentaje_bottleneck}% (nivel {c.nivel}), "
                f"limitado principalmente por {c.componente_limitante}."
            )
        if c.advertencias:
            partes.append(" Puntos a revisar: " + " ".join(c.advertencias))

        r = c.recomendacion
        if r is not None:
            if not r.componentes:
                partes.append(" No se encontro ninguna recomendacion dentro del presupuesto indicado.")
            elif "cpu" not in r.componentes and "gpu" not in r.componentes:
                partes.append(
                    " La recomendacion mantiene tu CPU y GPU actuales (no se encontro una alternativa que "
                    "mejore el rendimiento dentro del presupuesto) y ajusta: " + self._resumen_componentes(r) + "."
                )
            else:
                partes.append(" La recomendacion sugiere: " + self._resumen_componentes(r) + ".")
                if r.porcentaje_cuello_botella is not None:
                    partes.append(f" Cuello de botella resultante: {r.porcentaje_cuello_botella}%.")
        return "".join(partes)

    @staticmethod
    def _resumen_componentes(r) -> str:
        return ", ".join(f"{key}={comp.nombre}" for key, comp in r.componentes.items())
