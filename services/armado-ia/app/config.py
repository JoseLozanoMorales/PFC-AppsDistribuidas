"""
Configuracion del calculo de bottleneck y de la recomendacion. TODOS los
pesos/techos/margenes son valores explicitos con defaults documentados aqui
(equivalente a application.properties + BottleneckProperties en la version
Java), sobreescribibles por variable de entorno via pydantic-settings con
env_nested_delimiter="__" -- por ejemplo ARMADO_CPU__PESO_NUCLEOS=0.45. Nunca
hardcodeados en la logica de calculo (ver app/domain/*.py).
"""
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class CpuConfig(BaseModel):
    # nucleos y frecuencia pesan igual (ambos escalan computo bruto de forma
    # directa); hilos (SMT/Hyperthreading) pesa menos por rendimientos
    # decrecientes (~20-30%, no 2x). Techos: gama alta de consumo actual (2026).
    peso_nucleos: float = 0.40
    peso_frecuencia: float = 0.40
    peso_hilos: float = 0.20
    nucleos_max: float = 24
    frecuencia_turbo_ghz_max: float = 6.0
    hilos_max: float = 48


class GpuConfig(BaseModel):
    # nucleos (CUDA/stream processors) es el indicador mas directo de computo
    # paralelo, de ahi el peso mayor. VRAM determina resolucion/texturas
    # soportadas. TDP es el mas debil de los tres: proxy razonable de potencia
    # DENTRO de una generacion, pero no mide eficiencia por arquitectura.
    #
    # RECALIBRADO tras validar con el catalogo real: el techo original (16384
    # nucleos, equivalente a una GPU tope de gama) dejaba a la mejor GPU
    # disponible (RTX 4070 Ti, 7680 nucleos) en apenas 47% de su techo,
    # mientras la mejor CPU (i9-13900K, 24 nucleos) llegaba al 100% del suyo --
    # marco de referencia asimetrico entre categorias que hacia que la GPU
    # pareciera "el cuello de botella" casi sin importar que CPU se eligiera.
    # Estos techos dejan a la RTX 4070 Ti en ~83%, comparable al 92% del i9.
    peso_nucleos: float = 0.55
    peso_vram: float = 0.30
    peso_tdp: float = 0.15
    nucleos_max: float = 9000
    vram_gb_max: float = 16
    tdp_max: float = 320


class NivelConfig(BaseModel):
    moderado_desde: float = 20
    severo_desde: float = 50


class RamConfig(BaseModel):
    capacidad_minima_gb: float = 8


class PsuConfig(BaseModel):
    # Margen de holgura sobre el TDP combinado CPU+GPU. RECALIBRADO: con 1.3x
    # una PSU de 550W con i9(125W)+RTX4070Ti(285W)=410W combinado pasaba sin
    # advertencia (410*1.3=533W <= 550W) -- mecanicamente correcto pero laxo
    # frente a la guia real: NVIDIA recomienda 700W minimo para una RTX 4070
    # Ti, sobre todo por picos de consumo transitorio documentados en la
    # serie RTX 40. 700/410 ~= 1.7, de ahi el valor.
    margen_recomendado: float = 1.7


class CategoriaConfig(BaseModel):
    """
    Espejo DELIBERADO de productos.categoria_producto.nombre/obligatoria_pc/
    peso_presupuesto: productos-service no expone las ultimas 2 columnas por
    HTTP todavia (GET /api/categorias solo da id/nombre/slug). El
    emparejamiento con el catalogo real es por 'nombre' exacto (sin acentos a
    proposito, ver docs/seed-catalogo.sql). Las claves (cpu, mobo, ram, ...)
    coinciden con las que usa Apps/web/frontend/webapp/src/views/BuilderView.vue.
    """
    nombre: str
    obligatoria: bool
    peso_presupuesto: float


# Suman 1.00: CPU y GPU 30% cada una (las 2 categorias de computo, coherente
# con que dominan el score de bottleneck), motherboard 12%, RAM 10%,
# storage 8%, PSU 5%, case 3%, cooling+perifericos 1% cada una.
DEFAULT_CATEGORIAS: dict[str, CategoriaConfig] = {
    "cpu": CategoriaConfig(nombre="Procesador", obligatoria=True, peso_presupuesto=0.30),
    "mobo": CategoriaConfig(nombre="Motherboard", obligatoria=True, peso_presupuesto=0.12),
    "ram": CategoriaConfig(nombre="Memoria RAM", obligatoria=True, peso_presupuesto=0.10),
    "storage": CategoriaConfig(nombre="Almacenamiento", obligatoria=True, peso_presupuesto=0.08),
    "gpu": CategoriaConfig(nombre="Tarjeta grafica", obligatoria=False, peso_presupuesto=0.30),
    "psu": CategoriaConfig(nombre="Fuente de poder", obligatoria=True, peso_presupuesto=0.05),
    "case": CategoriaConfig(nombre="Gabinete", obligatoria=True, peso_presupuesto=0.03),
    "cooling": CategoriaConfig(nombre="Refrigeracion", obligatoria=False, peso_presupuesto=0.01),
    "periferico": CategoriaConfig(nombre="Perifericos", obligatoria=False, peso_presupuesto=0.01),
}


class ExplicacionConfig(BaseModel):
    proveedor: str = "bedrock"
    max_tokens: int = 400
    # Minimo del rango que acepta Bedrock Converse. Se bajo de 0.4 a 0 tras
    # detectar una alucinacion en produccion (el modelo inventaba una GPU y
    # una fuente de poder que no existian en el catalogo). No elimina el
    # riesgo por si solo -- por eso ademas el prompt esta endurecido (ver
    # app/explicacion/bedrock_client.py) y hay un validador post-generacion
    # (ver app/explicacion/service.py).
    temperature: float = 0


class HttpClientConfig(BaseModel):
    connect_timeout_ms: int = 2000
    read_timeout_ms: int = 5000


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ARMADO_", env_nested_delimiter="__")

    cpu: CpuConfig = CpuConfig()
    gpu: GpuConfig = GpuConfig()
    nivel: NivelConfig = NivelConfig()
    ram: RamConfig = RamConfig()
    psu: PsuConfig = PsuConfig()
    categorias: dict[str, CategoriaConfig] = DEFAULT_CATEGORIAS
    explicacion: ExplicacionConfig = ExplicacionConfig()
    http_client: HttpClientConfig = HttpClientConfig()

    # Nombres de variable de entorno ya establecidos por docker-compose.yml
    # (mismos que usaba la version Java), sin el prefijo ARMADO_.
    productos_service_base_url: str = Field(default="http://localhost:8081", alias="PRODUCTOS_SERVICE_URL")
    server_port: int = Field(default=8087, alias="SERVER_PORT")


settings = Settings()
