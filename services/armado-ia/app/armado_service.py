"""Orquestador. Puerto 1:1 de ArmadoService.java."""
import logging

from app.clients.producto_client import producto_client
from app.domain import advertencias as advertencias_tecnicas
from app.domain import bottleneck, recomendador
from app.errors import BadRequestError
from app.explicacion.client import ContextoExplicacion
from app.explicacion.service import ExplicacionService
from app.schemas import (
    AnalizarRequest,
    AnalizarResponse,
    ComponenteResponse,
    RecomendacionResponse,
)
from app.security import IdentidadOpcional

log = logging.getLogger("armado_ia.armado_service")


def analizar(request: AnalizarRequest, identidad: IdentidadOpcional,
             explicacion_service: ExplicacionService) -> AnalizarResponse:
    ids_solicitados = request.componentes or {}
    if ids_solicitados.get("cpu") is None:
        raise BadRequestError("El componente 'cpu' es obligatorio para el analisis")

    componentes = {
        key: producto_client.obtener_por_id(producto_id)
        for key, producto_id in ids_solicitados.items()
        if producto_id is not None
    }

    resultado = bottleneck.calcular(componentes["cpu"], componentes.get("gpu"))

    advertencias = list(resultado.advertencias)
    advertencias.extend(advertencias_tecnicas.evaluar(componentes))

    pide_recomendacion = request.presupuesto_maximo is not None
    recomendacion: RecomendacionResponse | None = None
    if pide_recomendacion:
        resultado_recomendacion = recomendador.recomendar(
            request.presupuesto_maximo, componentes["cpu"], componentes.get("gpu"), producto_client
        )
        componentes_recomendados = {
            key: ComponenteResponse(id=producto.id, nombre=producto.nombre, precio=float(producto.precio))
            for key, producto in resultado_recomendacion.componentes.items()
        }
        recomendacion = RecomendacionResponse(
            presupuesto_usado=float(resultado_recomendacion.presupuesto_usado),
            componentes=componentes_recomendados,
            porcentaje_cuello_botella=resultado_recomendacion.porcentaje_cuello_botella,
            nivel_cuello_botella=resultado_recomendacion.nivel_cuello_botella,
            componente_limitante=resultado_recomendacion.componente_limitante,
            advertencias=resultado_recomendacion.advertencias,
        )

    nombres_componentes = {key: producto.nombre for key, producto in componentes.items()}
    contexto = ContextoExplicacion(
        porcentaje_bottleneck=resultado.porcentaje,
        nivel=resultado.nivel,
        componente_limitante=resultado.componente_limitante,
        advertencias=advertencias,
        componentes_nombres=nombres_componentes,
        recomendacion=recomendacion,
    )
    explicacion = explicacion_service.generar(contexto)

    if identidad.presente:
        log.info("Analisis de armado -- usuario=%s (%s): %s%% nivel=%s limitante=%s",
                  identidad.user_id, identidad.username, resultado.porcentaje, resultado.nivel,
                  resultado.componente_limitante)
    else:
        log.info("Analisis de armado -- sin identidad: %s%% nivel=%s limitante=%s",
                  resultado.porcentaje, resultado.nivel, resultado.componente_limitante)

    return AnalizarResponse(
        porcentaje_cuello_botella=resultado.porcentaje,
        nivel=resultado.nivel,
        componente_limitante=resultado.componente_limitante,
        explicacion=explicacion,
        advertencias=advertencias,
        recomendacion=recomendacion,
    )
