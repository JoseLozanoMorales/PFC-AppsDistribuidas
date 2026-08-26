# ADR-008: Correspondencia de capas en Python y postura de verificación JWT en armado-ia

- Estado: aceptada
- Fecha: 2026-08-23
- Participación: decisión revisable conjuntamente por los cuatro integrantes

## Contexto

La rúbrica de la Entrega 4 (criterio D1.1) exige que cada microservicio tenga
cuatro paquetes (`presentation`, `application`, `domain`, `infrastructure`),
con entidades de dominio sin anotaciones de framework y con interfaces
(puertos) en `domain` implementadas como adaptadores en `infrastructure`. El
criterio D7.1 (seguridad) exige que el 100% de los endpoints tenga
verificación JWT.

Ambos criterios están redactados en términos de Java/Spring, el stack de los
otros seis microservicios del backend. `services/armado-ia` es Python/FastAPI
y, revisado tal cual está hoy, no usa esos cuatro nombres de carpeta ni
valida el JWT dentro del propio servicio. Sin este ADR, ambos puntos se leen
como incumplimiento directo de la rúbrica en la defensa oral. Este documento
registra por qué no lo son: la separación de capas existe con las
convenciones propias de Python, y la no verificación de JWT es una decisión
arquitectónica deliberada y coherente con ADR-007, no un descuido.

## Decisión 1: correspondencia de capas

Cada módulo de `armado-ia` cumple un rol equivalente al que jugaría en la
estructura de paquetes Java, aunque el nombre de carpeta no coincida
literalmente:

| Capa (rúbrica) | Módulo real en armado-ia | Rol |
|---|---|---|
| **domain** | `app/domain/models.py` | Entidades/value objects (`ProductoCatalogo`, `CategoriaInfo`) — `@dataclass` planos, cero imports de FastAPI/pydantic/httpx |
| **domain** | `app/domain/bottleneck.py` | Servicio de dominio: cálculo determinístico del porcentaje/nivel de cuello de botella |
| **domain** | `app/domain/recomendador.py` | Servicio de dominio: algoritmo de asignación de presupuesto CPU/GPU/RAM (dos pasadas, restricción de socket) |
| **domain** | `app/domain/advertencias.py` | Servicio de dominio: reglas de compatibilidad técnica |
| **domain (puerto)** | `app/explicacion/client.py` | `ExplicacionClient(Protocol)` — el puerto de explicación en lenguaje natural, sin implementación |
| **domain (puerto)** | `app/domain/catalogo_provider.py` | `CatalogoProvider(Protocol)` — el puerto de catálogo que necesita `recomendador.py` (`listar_categorias`, `listar_por_categoria`), segregado a solo esos dos métodos |
| **application** | `app/armado_service.py` | Orquestador de nivel superior — "Puerto 1:1 de `ArmadoService.java`" según su propio docstring: recibe la petición ya deserializada, llama a los servicios de dominio y al puerto de explicación, arma la respuesta; también inyecta `producto_client` como `CatalogoProvider` al llamar a `recomendador.recomendar()` |
| **application** | `app/explicacion/service.py` | `ExplicacionService` — orquesta cuál estrategia de `ExplicacionClient` ejecutar y aplica la cadena de validación (ver ADR-005) antes de aceptar una respuesta |
| **infrastructure** | `app/clients/producto_client.py` | Adaptador HTTP hacia `productos-service` (httpx + circuit breaker + retry); implementa `CatalogoProvider` de forma estructural (sin heredar de él ni importarlo) |
| **infrastructure** | `app/explicacion/bedrock_client.py` | Adaptador concreto del puerto `ExplicacionClient` que invoca AWS Bedrock |
| **infrastructure** | `app/config.py` | Carga de configuración/variables de entorno (`pydantic-settings`) |
| **presentation** | `app/main.py` | Rutas FastAPI (`@app.get`, `@app.post`) y composición de dependencias (equivalente al `@Controller` + configuración de beans) |
| **presentation** | `app/schemas.py` | DTOs de entrada/salida (`pydantic.BaseModel`), nunca reutilizados como entidad de dominio |
| **presentation** | `app/errors.py` | Registro de manejadores de excepción HTTP (equivalente a `@ControllerAdvice`) |
| **presentation** | `app/security.py` | Extrae identidad de las cabeceras del gateway (`X-User-Id`, `X-Usuario`, `X-User-Role`) e inyecta `IdentidadOpcional` vía `Depends` (equivalente al `AuthUsuarioArgumentResolver` de `pedidos-service`) |

La propiedad que exige la rúbrica —dominio libre de anotaciones de framework—
se verifica directamente: `app/domain/*.py` no importa `fastapi`,
`pydantic` ni `httpx` en ningún archivo, **ni tampoco `app.clients` ni
ningún otro módulo de infraestructura**: `recomendador.py` recibe el
catálogo como parámetro tipado con `CatalogoProvider` en vez de importar
`producto_client` directamente, así que la dirección de dependencias va de
`infrastructure` hacia `domain` (a través del `Protocol`), nunca al revés.
La propiedad de puerto/adaptador se verifica en dos lugares: en
`explicacion/`, `ExplicacionClient` es un `Protocol` sin dependencia de AWS,
y `BedrockExplicacionClient` (infraestructura) y
`DeterministicExplicacionClient` (cómputo puro) lo implementan de forma
intercambiable — documentado como Strategy en ADR-005; y en
`domain/catalogo_provider.py`, `CatalogoProvider` es el puerto que
`producto_client` cumple de forma estructural (duck typing vía `Protocol`,
sin heredar de él), inyectado por `armado_service.py` (capa de aplicación)
en cada llamada a `recomendador.recomendar()`.

### Por qué no se movieron archivos a carpetas `application/` y `presentation/`

Se evaluó crear esas dos carpetas y no se hizo, por dos razones concretas:

1. **No habría ninguna frontera real que hacer cumplir.** Python no tiene un
   equivalente al *package-private* de Java ni a los módulos con
   `exports`/`requires` explícitos: cualquier archivo puede importar
   cualquier otro sin que el intérprete valide nada sobre la carpeta en la
   que vive. Mover `armado_service.py` a `application/armado_service.py` no
   impediría que `domain/recomendador.py` importara algo de
   `infrastructure/` mañana — el aislamiento actual (dominio limpio,
   verificado por ausencia de imports de framework) es una disciplina del
   equipo, no una regla que el sistema de imports haga cumplir en ningún
   caso, muevas los archivos o no.
2. **El movimiento habría sido cosmético y habría fragmentado un servicio
   pequeño.** `application/` terminaría conteniendo esencialmente
   `armado_service.py` como único orquestador de nivel superior —
   `explicacion/service.py` ya vive dentro de su propio módulo de feature
   cohesivo junto a su puerto y sus adaptadores, y separarlo en una carpeta
   `application/` distinta habría roto esa cohesión sin ganar nada
   verificable. Del mismo modo, `presentation/` terminaría siendo
   prácticamente sinónimo de `main.py` como único punto de entrada HTTP.
   Renombrar carpetas para que coincidan con los nombres literales de la
   rúbrica sin que cambie ninguna propiedad estructural del código (qué
   importa a qué) habría sido teatro, no arquitectura.

La convención elegida en Python para expresar la misma separación es
**organización por módulo de dominio/feature cohesivo** (`domain/`,
`explicacion/`, `clients/`) más **`Protocol` como mecanismo de puerto**, en
vez de una jerarquía de paquetes anidados por capa técnica. Es el patrón
idiomático en FastAPI (a diferencia de Spring, que sí tiene un contenedor de
inyección de dependencias y un classloader que hacen la jerarquía de
paquetes más significativa en tiempo de ejecución).

## Decisión 2: postura de verificación JWT en `POST /api/armado/analizar`

`armado-ia` no valida el JWT dentro del propio servicio. El único endpoint
de negocio, `POST /api/armado/analizar`, usa `identidad_opcional`
(`app/security.py`), que nunca bloquea la petición aunque falten las
cabeceras `X-User-Id`/`X-Usuario`/`X-User-Role`.

Esto es una extensión deliberada del patrón centralizado de ADR-007, no una
omisión:

- **El gateway ya es el único punto de validación de JWT en toda la
  arquitectura** (ADR-007). Duplicar esa validación dentro de `armado-ia`
  contradiría explícitamente la regla de ADR-007 de que "los servicios
  internos no vuelven a validar el JWT: el punto de confianza es el gateway
  y el aislamiento de la red Docker".
- **El puerto de `armado-ia` no se publica al host.** Verificado en
  `docker-compose.yml`: `tiendatech-armado-ia` no tiene bloque `ports:`, solo
  pertenece a las redes `default` y `tiendatech-net`. La única forma de alcanzar
  `/api/armado/analizar` desde fuera de la red Docker interna es a través
  del gateway, que ya filtró la petición antes de reenviarla.
- **El servicio no persiste nada.** `armado-ia` es cómputo puro sobre el
  catálogo de `productos-service`: no hay tabla propia, no hay estado por
  usuario que sobreviva a la petición.
- **La respuesta no expone datos de ningún usuario concreto.** El análisis
  de cuello de botella y la recomendación de armado dependen únicamente de
  los componentes de hardware solicitados y del catálogo público de
  productos — no hay biografía, pedido, dirección ni dato personal en el
  cuerpo de la petición ni de la respuesta.
- **La identidad, cuando está presente, se usa solo para logging.** Si el
  gateway sí propagó `X-User-Id`, `armado_service.analizar()` lo registra en
  el log de aplicación (`log.info(...)`) para trazabilidad, pero ninguna
  rama de negocio depende de que esté presente.

Dado que no hay dato sensible que proteger y que el aislamiento de red ya
impide el acceso directo, bloquear la petición cuando faltan las cabeceras
añadiría una verificación redundante sin reducir superficie de ataque real:
un cliente que pudiera llegar a `tiendatech-armado-ia:8087` sin pasar por el
gateway también podría fabricar las cabeceras `X-User-*` que un chequeo
"obligatorio" estaría validando, porque `armado-ia` no tiene forma
independiente de verificar la firma del JWT sin duplicar el secreto y la
lógica que ya vive en `usuarios-service`/gateway.

### Caso análogo en pedidos-service

`pedidos-service` sigue la misma arquitectura de confianza en el gateway
(ADR-007), pero con matices distintos por el tipo de dato que expone. De sus
12 endpoints, 11 exigen `@AuthUsuario` vía `AuthUsuarioArgumentResolver`
(responde `401` si falta `X-User-Id`). La única excepción es
`GET /metodos-pago/tipos`, que devuelve el catálogo estático de tipos de
método de pago (ej. "tarjeta", "transferencia") — no depende de qué usuario
pregunta ni devuelve dato de ningún usuario concreto, exactamente el mismo
criterio que exime a `armado-ia`: sin dato sensible que proteger, exigir
identidad ahí sería una verificación cosmética, no una que reduzca riesgo
real.

## Alternativas consideradas

1. **Renombrar carpetas a `presentation/`, `application/`, `domain/`,
   `infrastructure/` para que coincidan literalmente con la rúbrica.**
   Descartada: no cambia ninguna propiedad verificable del código (qué
   importa a qué, qué tiene anotaciones de framework), solo la ubicación en
   disco de archivos que ya cumplen el rol correcto — indirección sin
   beneficio, riesgo de romper imports sin ganar nada a cambio.
2. **Hacer que `identidad_opcional` bloquee con `401` si faltan las
   cabeceras, para cumplir la letra literal de "100% de endpoints con
   verificación JWT".** Es la alternativa más barata de implementar (~10
   líneas) y se consideró seriamente. Se descartó por ahora porque
   introduciría una verificación que no protege ningún dato real y que
   duplicaría, de forma incompleta (sin validar firma ni expiración), la
   responsabilidad que ADR-007 ya centraliza en el gateway. Queda como
   opción de bajo costo si el criterio se interpreta de forma estrictamente
   literal en la evaluación.
3. **Elegida:** documentar la correspondencia de capas tal como existe hoy,
   cerrar la desviación real de Ports & Adapters que tenía `recomendador.py`
   introduciendo el puerto `CatalogoProvider`, y adoptar la postura de
   confianza en el gateway como decisión explícita para JWT — en vez de
   renombrar carpetas por cosmética o duplicar validación sin necesidad.

## Consecuencias

- `armado-ia` queda documentado como arquitectónicamente equivalente a los
  cuatro paquetes que pide D1.1, con la tabla de correspondencia como
  evidencia verificable en la defensa oral.
- `app/domain/recomendador.py` ya no importa `app.clients.producto_client`:
  recibe el catálogo a través del puerto `CatalogoProvider`
  (`app/domain/catalogo_provider.py`), inyectado por `armado_service.py`
  (capa de aplicación) en cada llamada. El dominio de `armado-ia` queda sin
  ningún import de infraestructura, verificado por inspección directa de
  `app/domain/*.py` — ya no hay desviación de Ports & Adapters que
  documentar como excepción.
- La ausencia de verificación JWT propia en `armado-ia` queda justificada
  por escrito y ligada a condiciones verificables (sin `ports:` en
  `docker-compose.yml`, sin persistencia, sin dato de usuario en la
  respuesta). Si cualquiera de esas tres condiciones cambia — por ejemplo,
  si `armado-ia` empezara a persistir historial de análisis por usuario—,
  este ADR queda invalidado y debe revisarse.
- Si el criterio D7.1 se evalúa de forma estrictamente literal ("100% de
  endpoints", sin excepción), la Alternativa 2 (bloquear en
  `identidad_opcional`) queda documentada como el cambio mínimo disponible
  para cumplirlo sin rediseñar nada.
