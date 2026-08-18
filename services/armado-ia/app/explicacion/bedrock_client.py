"""
Credenciales NUNCA se leen aqui: boto3.client("bedrock-runtime") usa la
cadena de proveedores por defecto del SDK de AWS para AWS_ACCESS_KEY_ID /
AWS_SECRET_ACCESS_KEY por si sola. La region es la unica excepcion: a
diferencia del SDK de Java (que si revisa AWS_REGION automaticamente),
boto3 solo resuelve la region por defecto desde AWS_DEFAULT_REGION o
~/.aws/config -- verificado en vivo (NoRegionError al no pasarla). Por eso se
lee explicitamente la variable de entorno AWS_REGION aqui abajo; sigue sin
haber ningun valor literal de region/credenciales escrito en el codigo.

Se usa la Converse API (no invoke_model) precisamente porque es agnostica del
proveedor del modelo (Anthropic, Meta, Amazon, etc.): cambiar de modelo es
solo cambiar BEDROCK_MODEL_ID, sin tocar este codigo. Mismo modelo que la
version Java: amazon.nova-lite-v1:0 (via BEDROCK_MODEL_ID).
"""
import os

import boto3

from app.config import settings
from app.explicacion.client import ContextoExplicacion


# Prompt endurecido tras detectar alucinacion en produccion: el modelo
# inventaba una "RTX 3070" y una "fuente de 650W" que no existen en el
# catalogo, en el hueco que dejaba "tambien se genero una recomendacion:
# mencionalo brevemente" -- sin dato concreto, el modelo lo completaba con
# conocimiento propio de hardware. Ahora se le da el detalle completo y
# exacto de la recomendacion (o se le dice explicitamente que NO hay cambio
# de CPU/GPU), y se prohibe explicitamente nombrar cualquier cosa que no
# este en este mensaje.
class BedrockExplicacionClient:
    def __init__(self):
        self._model_id = os.environ.get("BEDROCK_MODEL_ID", "")
        self._max_tokens = settings.explicacion.max_tokens
        self._temperature = settings.explicacion.temperature
        self._client = boto3.client("bedrock-runtime", region_name=os.environ.get("AWS_REGION"))

    def explicar(self, contexto: ContextoExplicacion) -> str:
        if not self._model_id:
            raise RuntimeError("BEDROCK_MODEL_ID no configurado")

        response = self._client.converse(
            modelId=self._model_id,
            messages=[{"role": "user", "content": [{"text": self._construir_prompt(contexto)}]}],
            inferenceConfig={"maxTokens": self._max_tokens, "temperature": self._temperature},
        )
        contenido = response["output"]["message"]["content"]
        if not contenido:
            raise RuntimeError("Bedrock devolvio una respuesta sin contenido")
        return contenido[0]["text"]

    def _construir_prompt(self, c: ContextoExplicacion) -> str:
        partes: list[str] = []
        partes.append(
            "Sos un asistente que redacta, en espanol, UN SOLO parrafo breve (maximo 120 palabras) que "
            "resume EXCLUSIVAMENTE los datos listados abajo, resultado de un analisis tecnico ya calculado.\n"
            "REGLA ABSOLUTA: no tenes conocimiento propio de hardware. NUNCA menciones un componente, "
            "modelo, marca, capacidad, velocidad, potencia o cualquier otra especificacion que no aparezca "
            "LITERALMENTE en los datos de abajo. Si un dato no esta, no lo menciones ni lo inventes -- omitilo. "
            "No redondees ni cambies numeros. No agregues opiniones ni recomendaciones propias.\n\n"
        )

        partes.append("Configuracion actual del usuario:\n")
        for key, nombre in c.componentes_nombres.items():
            partes.append(f"- {key}: {nombre}\n")

        partes.append(f"\nPorcentaje de cuello de botella (CPU vs GPU) de ESTA configuracion: "
                       f"{c.porcentaje_bottleneck}% (nivel {c.nivel})\n")
        partes.append(f"Componente limitante: {c.componente_limitante}\n")

        if c.advertencias:
            partes.append("\nAdvertencias tecnicas sobre la configuracion actual:\n")
            for advertencia in c.advertencias:
                partes.append(f"- {advertencia}\n")

        r = c.recomendacion
        if r is not None:
            partes.append("\nSe pidio ademas una recomendacion de configuracion alternativa bajo presupuesto.\n")
            if not r.componentes:
                partes.append("La recomendacion no pudo incluir ningun componente dentro del presupuesto.\n")
            else:
                partes.append("Componentes que la recomendacion SI incluye (son los UNICOS que podes nombrar "
                               "al hablar de la recomendacion):\n")
                for key, comp in r.componentes.items():
                    partes.append(f"- {key}: {comp.nombre} (${comp.precio})\n")

            sin_cambio_cpu_gpu = "cpu" not in r.componentes and "gpu" not in r.componentes
            if sin_cambio_cpu_gpu:
                partes.append(
                    "IMPORTANTE: la recomendacion NO cambia la CPU ni la GPU actuales del usuario (no se "
                    "encontro una alternativa que mejore el rendimiento dentro del presupuesto). Decilo "
                    "explicitamente. NO sugieras ningun modelo de CPU o GPU distinto al de la configuracion "
                    "actual listada arriba.\n"
                )
            if r.porcentaje_cuello_botella is not None:
                partes.append(f"Porcentaje de cuello de botella de la configuracion recomendada: "
                               f"{r.porcentaje_cuello_botella}%\n")
            if r.advertencias:
                partes.append("Advertencias sobre la recomendacion:\n")
                for advertencia in r.advertencias:
                    partes.append(f"- {advertencia}\n")

        partes.append(
            "\nRecorda la regla absoluta: SOLO podes nombrar los componentes, modelos y numeros que "
            "aparecen escritos arriba en este mensaje. Nada mas."
        )
        return "".join(partes)
