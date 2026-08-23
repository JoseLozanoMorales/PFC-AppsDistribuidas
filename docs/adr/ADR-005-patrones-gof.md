# ADR-005: Patrones de diseño GoF aplicados en el backend

- Estado: aceptada
- Fecha: 2026-08-23
- Participación: decisión revisable conjuntamente por los cuatro integrantes

## Contexto

La entrega exige documentar los patrones de diseño GoF (*Gang of Four*,
Gamma/Helm/Johnson/Vlissides, 1994) realmente presentes en el backend, con un
mínimo de cinco patrones identificados por nombre, propósito y ubicación en el
código.

Se auditaron los ocho servicios del backend (`pedidos-service`, `armado-ia`,
`ordenes-proveedores-service`, `ventas-service`, `inventario-service`,
`productos-service`, `usuarios`) y el gateway (`Apps/web/frontend`,
`JwtGatewayFilter`). El resultado honesto de esa auditoría es que **la mayoría
de los servicios son arquitectura hexagonal/por capas limpia sin patrones GoF
genuinos**: lo que a primera vista parece un patrón es en realidad inyección
de dependencias de Spring, un puerto con una única implementación (Repository
de Fowler, no GoF), o un builder/decorador de una librería de terceros
(`RestClient.builder()`, `Jwts.builder()`, `CircuitBreaker.decorateSupplier`
de resilience4j).

Se encontraron **cuatro** patrones GoF con evidencia real (tres sólidos y uno
parcial, apoyado en infraestructura de framework). Para llegar al mínimo
exigido sin forzar ni inventar patrones donde no los hay, se decidió hacer un
cambio de código mínimo y legítimo en `services/armado-ia`: extraer el
fallback determinista de `ExplicacionService` a una clase propia que
implementa el mismo `Protocol` que el cliente de Bedrock. Esto convierte un
Strategy incompleto (una sola implementación real) en un Strategy real (dos
estrategias intercambiables), y de paso mejora el diseño: antes el fallback
era un método privado mezclado con la lógica de selección y validación
anti-alucinación; ahora es una responsabilidad separada y testeable por sí
sola.

No se tocó ningún otro servicio para completar la cuota. Donde el framework ya
resuelve el problema sin necesitar un patrón GoF propio, se documenta así
explícitamente en vez de forzarlo.

## Decisión: patrones GoF documentados

### 1. Strategy (comportamiento)

**Propósito canónico (GoF):** definir una familia de algoritmos, encapsular
cada uno y hacerlos intercambiables. Strategy permite que el algoritmo varíe
independientemente de los clientes que lo usan.

**Ubicación:**
- Interfaz (Strategy): `services/armado-ia/app/explicacion/client.py:24`,
  `ExplicacionClient(Protocol)` con el método `explicar(contexto: ContextoExplicacion) -> str`.
- Estrategia concreta 1: `services/armado-ia/app/explicacion/bedrock_client.py:32`,
  `BedrockExplicacionClient.explicar()` — invoca la Converse API de AWS Bedrock.
- Estrategia concreta 2: `services/armado-ia/app/explicacion/fallback_client.py`,
  `DeterministicExplicacionClient.explicar()` — redacta la explicación solo con
  los datos ya calculados, sin LLM.
- Contexto: `services/armado-ia/app/explicacion/service.py`, `ExplicacionService`
  recibe ambas estrategias por constructor (`self._cliente`, `self._fallback`) y
  decide en runtime cuál ejecutar dentro de `generar()`.
- Composición: `services/armado-ia/app/main.py:27`,
  `ExplicacionService(_crear_explicacion_client(), DeterministicExplicacionClient())`.

**Problema que resuelve en este proyecto:** permite cambiar el proveedor de
explicación en lenguaje natural (Bedrock hoy, otro LLM mañana) sin tocar
`ExplicacionService`, y garantiza que siempre exista una estrategia de
resguardo con la misma interfaz cuando el LLM no está configurado, falla, o el
validador anti-alucinación descarta su respuesta — sin ese `if/else` mezclado
con lógica de negocio.

### 2. Adapter (estructural)

**Propósito canónico (GoF):** convertir la interfaz de una clase en otra
interfaz que los clientes esperan. Adapter permite que clases con interfaces
incompatibles colaboren.

**Ubicación:**
- `services/armado-ia/app/explicacion/bedrock_client.py:32`,
  `BedrockExplicacionClient` — envuelve `boto3.client("bedrock-runtime")` y lo
  expone como `ExplicacionClient.explicar(ContextoExplicacion) -> str`, la
  interfaz de dominio propia.
- `services/armado-ia/app/clients/producto_client.py:84`, `ProductoClient` —
  envuelve `httpx` y traduce el JSON crudo del catálogo a `ProductoCatalogo`/
  `CategoriaInfo` de dominio vía `_mapear()` (línea 121).

**Problema que resuelve en este proyecto:** aísla el dominio de los detalles
del SDK de AWS y de HTTP/JSON crudo, de modo que la lógica de negocio
(`armado_service`, `ExplicacionService`) trabaja contra tipos e interfaces
propias y puede probarse o cambiar de proveedor sin modificar esa lógica.

### 3. Decorator (estructural)

**Propósito canónico (GoF):** adjuntar responsabilidades adicionales a un
objeto dinámicamente. Decorator ofrece una alternativa flexible a la herencia
para extender funcionalidad.

**Ubicación:** `Apps/web/frontend/src/main/java/com/tiendatech/frontend/security/JwtGatewayFilter.java`,
clase interna `TrustedUserHeaderRequest extends HttpServletRequestWrapper`.
Envuelve el `HttpServletRequest` original conservando su interfaz
(`getHeader`, `getHeaders`, `getHeaderNames`) y sobrescribe esos métodos para
inyectar las cabeceras de identidad verificadas (`X-User-Id`, `X-Usuario`,
`X-User-Role`) sin tocar la clase `HttpServletRequest` ni el resto de la
cadena de filtros/controladores que la consumen después.

**Problema que resuelve en este proyecto:** propagar identidad ya validada
por el gateway (ver ADR-007) a los microservicios internos, envolviendo la
petición en vez de mutarla o crear un tipo paralelo — el resto del pipeline
sigue viendo un `HttpServletRequest` normal.

### 4. Chain of Responsibility (comportamiento) — aplicación parcial

**Propósito canónico (GoF):** evitar acoplar el emisor de una petición a su
receptor, dando a más de un objeto la oportunidad de manejarla. Se encadenan
los objetos receptores y la petición pasa por la cadena hasta que alguno la
maneja.

**Ubicación:** `Apps/web/frontend/.../security/JwtGatewayFilter.java`,
`JwtGatewayFilter extends OncePerRequestFilter`, método `doFilterInternal(request, response, filterChain)`:
decide entre tres salidas — dejar pasar la petición sin tocar, dejarla pasar
envuelta en `TrustedUserHeaderRequest`, o cortar con 401 — delegando al
siguiente eslabón vía `filterChain.doFilter(...)`.

**Aclaración importante:** esta es la cadena de responsabilidad del Servlet
API / Spring Boot, infraestructura de terceros que el proyecto **usa**, no
construye desde cero. El proyecto añade un único eslabón propio
(`JwtGatewayFilter`) sobre esa cadena; no hay una jerarquía de *handlers*
propios encadenados entre sí con `setNext()`. Se documenta como aplicación
parcial y no como una implementación GoF completa, para no sobrevender el
diseño.

**Problema que resuelve en este proyecto:** decidir en un único punto
reutilizable del pipeline HTTP si una petición sigue hacia el microservicio
interno, sigue con identidad enriquecida, o se corta antes de llegar a los
controladores — sin duplicar esa decisión en cada endpoint.

## Patrones evaluados y descartados explícitamente

Para que quede constancia de que no se fuerza nada: se revisaron y
descartaron por no tener estructura GoF real —

- **Strategy** en `pedidos-service`, `ordenes-proveedores-service`,
  `ventas-service`, `inventario-service`, `productos-service` y `usuarios`:
  todos sus puertos de dominio (`CarritoRepository`, `OrdenRepository`,
  `FacturaStore`, `ProductoRepository`, `TokenPort`, etc.) tienen **una sola**
  implementación JDBC/adaptador. Es Repository (Fowler), no Strategy.
- **Factory Method / Abstract Factory** en todo el backend: los `@Bean` de
  Spring (`RestClientConfig`, `JwtConfig`) no tienen lógica condicional de
  creación de variantes; los `crear()`/`_crear_explicacion_client()` sin
  jerarquía Creator/ConcreteCreator son *simple factories* procedurales, no
  Factory Method GoF.
- **Builder** en todo el backend: los únicos `.builder()` son de librerías de
  terceros (`RestClient.builder()` de Spring, `Jwts.builder()` de JJWT) o
  `@Builder` de Lombok sobre DTOs planos sin pasos obligatorios ni validación
  de secuencia (`UsuarioAdminDTO`) — no construcción compleja paso a paso.
- **Observer**: no hay `ApplicationEventPublisher`/`@EventListener` en ningún
  servicio Java. Los `dispatchEvent`/`addEventListener` del frontend
  (`Apps/web/frontend/webapp/src/services/session.ts`) son la API nativa de
  eventos del navegador, no una implementación deliberada de Observer GoF.
- **Facade**: `ProductoService`, `InventarioService`, etc. delegan 1:1 a su
  repositorio sin coordinar ni simplificar varios subsistemas.
- **Template Method, Command, State, Singleton**: sin evidencia en ningún
  servicio (cero clases abstractas propias; los `switch` sobre `String` en
  `usuarios` son *dispatch* condicional, no objetos `Command`/`State`
  polimórficos; los beans `@Service`/`@Repository` de Spring son singletons
  implícitos del contenedor, no Singleton GoF).

## Patrones NO GoF presentes (de otras fuentes, documentados aparte)

Estos patrones existen y son legítimos en la arquitectura, pero **no
pertenecen al catálogo GoF**; se listan por separado para no mezclarlos:

- **Repository** (Martin Fowler, *Patterns of Enterprise Application
  Architecture*, 2002): puerto + adaptador JDBC en los seis servicios Java
  (`domain/*Repository` + `infrastructure/persistence/Jdbc*Repository`).
- **Outbox** (Chris Richardson, microservices.io — patrón de integración/
  mensajería): `services/ventas-service`, `FacturaOutboxRepository` +
  `InventarioOutboxProcessor.reintentarPendientes()` (`@Scheduled`).
- **Circuit Breaker** y **Retry** (Michael Nygard, *Release It!*, 2007):
  resilience4j en `services/pedidos-service` (`FacturaClient`) y
  `services/ordenes-proveedores-service` (`InventarioClient`); `pybreaker` +
  `tenacity` en `services/armado-ia` (`ProductoClient`).

## Alternativas consideradas

1. Documentar solo los 2-3 patrones sólidos que ya existían sin tocar código:
   honesto, pero no llega al mínimo de cinco exigido por la rúbrica.
2. Forzar la etiqueta GoF sobre Repository, DI de Spring o builders de
   terceros en varios servicios: se descartó por ser inexacto y frágil ante
   una revisión de código.
3. Refactorizar varios servicios (`productos-service`, `pedidos-service`, etc.)
   para sembrar patrones adicionales: se descartó por alcance — esos servicios
   no son responsabilidad de quien redactó este ADR y no se justifica tocar
   código ajeno solo para cumplir una cuota.
4. **Elegida:** un único cambio mínimo y legítimo en `armado-ia` (extraer el
   fallback a una segunda estrategia real), documentando el resto tal como
   está, con una sección aparte para no-GoF.

## Consecuencias

- El backend documenta honestamente **4 patrones GoF** (3 sólidos: Strategy,
  Adapter, Decorator; 1 parcial: Chain of Responsibility apoyado en Servlet
  API), no 5 forzados. Si la rúbrica exige estrictamente 5 patrones GoF
  distintos sin excepción, esto debe discutirse con la cátedra antes de la
  entrega; forzar un quinto patrón inexistente no es una opción preferida por
  el equipo.
- El cambio en `services/armado-ia/app/explicacion/` es funcional, no
  cosmético: `ExplicacionService` ya no mezcla selección de estrategia con la
  lógica de redacción del fallback, y `DeterministicExplicacionClient` puede
  probarse de forma aislada.
- La mayoría del backend seguirá sin patrones GoF explícitos, lo cual es
  correcto para su tamaño y problema: forzar patrones donde el framework
  (Spring DI, Repository) ya resuelve el problema habría sido sobre-ingeniería.
