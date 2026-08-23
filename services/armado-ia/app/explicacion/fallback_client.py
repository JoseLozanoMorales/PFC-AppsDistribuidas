"""
Segunda implementacion de ExplicacionClient (ver client.py): redacta una
explicacion determinista usando solo los datos ya calculados, sin LLM de por
medio. Es la estrategia que ExplicacionService usa cuando no hay proveedor de
IA configurado, o como resguardo si el proveedor falla o alucina.
"""
from app.explicacion.client import ContextoExplicacion


class DeterministicExplicacionClient:
    def explicar(self, contexto: ContextoExplicacion) -> str:
        partes: list[str] = []
        if contexto.nivel == "N/A":
            partes.append(
                "Esta configuracion no incluye una GPU dedicada, por lo que no aplica el calculo de "
                "cuello de botella CPU-GPU."
            )
        else:
            partes.append(
                f"Se detecto un cuello de botella del {contexto.porcentaje_bottleneck}% (nivel {contexto.nivel}), "
                f"limitado principalmente por {contexto.componente_limitante}."
            )
        if contexto.advertencias:
            partes.append(" Puntos a revisar: " + " ".join(contexto.advertencias))

        r = contexto.recomendacion
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
