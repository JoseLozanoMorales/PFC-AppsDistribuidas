"""
Metricas de negocio (D6.1). Las metricas HTTP genericas (http_requests_total,
http_request_duration_seconds) las instrumenta prometheus-fastapi-
instrumentator automaticamente (ver main.py) -- este modulo es solo para lo
que la instrumentacion generica no puede saber: eventos de negocio.

Un unico contador de eventos, igual que en pedidos-service (ver
infrastructure.config.BusinessMetrics del lado Java): app_business_events_total
con evento/resultado como etiquetas, en vez de un contador nuevo por evento.

app_explicacion_estrategia_total es especifico de este servicio: distingue
cuantos analisis terminaron explicados via Bedrock vs cuantos cayeron al
fallback deterministico (ver ExplicacionService) -- valioso para la demo en
vivo, no forma parte del minimo de 4 metricas de la rubrica.
"""
from prometheus_client import Counter

_EVENTOS = Counter(
    "app_business_events",
    "Eventos de negocio completados o fallidos, por tipo de evento",
    ["evento", "resultado"],
)

_ESTRATEGIA_EXPLICACION = Counter(
    "app_explicacion_estrategia",
    "Analisis explicados por Bedrock vs fallback deterministico",
    ["estrategia"],
)


def registrar_analisis_completado() -> None:
    _EVENTOS.labels(evento="analisis_armado", resultado="completado").inc()


def registrar_analisis_fallido() -> None:
    _EVENTOS.labels(evento="analisis_armado", resultado="fallido").inc()


def registrar_explicacion_bedrock() -> None:
    _ESTRATEGIA_EXPLICACION.labels(estrategia="bedrock").inc()


def registrar_explicacion_fallback() -> None:
    _ESTRATEGIA_EXPLICACION.labels(estrategia="fallback").inc()
