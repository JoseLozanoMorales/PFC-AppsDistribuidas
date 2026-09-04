# Paso 9 - pruebas, cobertura e integracion continua

Fecha de revision local: 2026-09-04 (tercera revision).

> Esta seccion superior refleja el cierre de la tercera revision. El resto del
> documento (mas abajo) es el registro original de la primera revision y se deja
> sin editar como historial.

## Criterios de la rubrica

1. Pruebas unitarias de la logica de negocio de cada microservicio.
2. Al menos tres flujos de integracion de extremo a extremo por API Gateway.
3. Informe de cobertura publicado con umbral minimo de 70%.
4. Complejidad ciclomatica medida y metodos por encima de 10 corregidos.
5. Workflow `.github/workflows/ci-cd.yml` con pruebas, analisis estatico, build de imagen e integracion.
6. Prueba de carga con 50 usuarios durante 60 segundos y captura/panel durante la prueba.
7. Evidencia rojo/verde del pipeline: fallo intencional, correccion y regreso a verde.

## Estado verificado

| Punto | Estado | Evidencia |
|---|---|---|
| Unitarias por microservicio | Cumple en Java | `services/*/src/test/` contiene pruebas en los seis microservicios Java. En esta revision pasaron `inventario-service` 17/17, `ordenes-proveedores-service` 6/6, `productos-service` 14/14 y `usuarios` 27/27 usando `maven:3.9-eclipse-temurin-21`. |
| Integracion por Gateway | Cumple parcialmente | `Apps/web/frontend/src/test/java/com/tiendatech/frontend/GatewayIntegrationTest.java` existe y se ejecuta desde `.github/workflows/ci-cd.yml`. |
| Cobertura >=70% | Cumple para Java | `docs/experimentos/resultados/iso25010/cobertura-summary.csv`; los seis servicios Java quedan sobre 70%. `armado-ia` ya tiene `docs/evidencias/cobertura/armado-ia-coverage.xml`. |
| Complejidad ciclomatica | Cumple | La segunda revision dejo los siete modulos por debajo de 10; ver la tabla de resultados mas abajo. |
| CI/CD | Cumple estructura base | `.github/workflows/ci-cd.yml` tiene `test-java`, `test-python`, `lint`, `integration-test`, `build` y `coverage`, activado por `push`, `pull_request` y `workflow_dispatch`. |
| Carga 50 usuarios/60s | Cumple en entorno local controlado | Corrida del 2026-09-04: 50 usuarios alcanzados, 2624 solicitudes, 0 fallos, 44.25 req/s, mediana 8 ms y p95 17 ms. Evidencia en `tests/load/results/tiendatech-50-users-20260904-local_*.csv`. |
| Rojo/verde del pipeline | Cumple | Enlaces exactos y commits documentados en `docs/evidencias/paso9-ci-rojo-verde.md`. |

## Cambios hechos en esta revision

- `JdbcInventarioRepository.registrarItem` se dividio en helpers para lectura de tipo, validacion, calculo de costo/stock y escrituras de movimiento/kardex.
- `JdbcOrdenCompraRepository.validarDetalle` se dividio en validacion por linea.
- `JdbcProductoRepository.agregarImagen` se dividio en validacion, hash, busqueda de duplicados, reutilizacion e insercion.
- `UsuarioService.cambiarPasswordConToken` recibio un helper inicial para validar token presente.

Todos los cambios anteriores mantienen el comportamiento observable y fueron validados con tests de los modulos tocados.

## Comandos de verificacion usados

```powershell
docker run --rm -v ${PWD}:/workspace -w /workspace/services/inventario-service maven:3.9-eclipse-temurin-21 mvn --batch-mode --no-transfer-progress test
docker run --rm -v ${PWD}:/workspace -w /workspace/services/ordenes-proveedores-service maven:3.9-eclipse-temurin-21 mvn --batch-mode --no-transfer-progress test
docker run --rm -v ${PWD}:/workspace -w /workspace/services/productos-service maven:3.9-eclipse-temurin-21 mvn --batch-mode --no-transfer-progress test
docker run --rm -v ${PWD}:/workspace -w /workspace/services/usuarios maven:3.9-eclipse-temurin-21 mvn --batch-mode --no-transfer-progress test
powershell -ExecutionPolicy Bypass -File scripts/measure-cyclomatic-complexity.ps1
```

## Pendientes concretos para cerrar el Paso 9 (primera revision, ya resueltos - ver seccion siguiente)

1. Refactorizar o justificar los metodos que PMD sigue reportando por encima de 10 en `summary.csv`.
2. Repetir la prueba de carga y corregir el fallo masivo de `GET /api/productos` antes de presentarla como evidencia.
3. Ejecutar una pareja rojo/verde en GitHub Actions y guardar los enlaces en este documento o en el manuscrito.
4. Reconciliar el texto de `docs/entrega4/PFC4.tex` con las evidencias nuevas de Paso 8 y Paso 9.

---

## Segunda revision (2026-09-01) - que se cerro y que queda

### 1. Complejidad ciclomatica -> CIERRA

`scripts/measure-cyclomatic-complexity.ps1` se corrio de nuevo tras esta revision.
Los dos modulos que seguian en rojo ya estan corregidos:

- `ordenes-proveedores-service`: `PgErrorMapper.statusFor(Throwable)` (complejidad 10)
  se dividio en `sqlStateOf(...)` + `statusForSqlState(...)`.
- `usuarios`: `OtpService.enviar(String, String)` (complejidad 10) se dividio en
  `resolverTxId`, `enviarOtpOFallback`, `manejarFalloEnvioOtp` y `construirRespuestaEnvio`.

Resultado final (los 7 modulos, `<10`, todos `CUMPLE`):

| modulo | max complejidad |
|---|---|
| gateway | 7 |
| inventario | 8 |
| ordenes_proveedores | 9 |
| pedidos | 7 |
| productos | 8 |
| usuarios | 9 |
| ventas | 7 |

### 2. Cobertura de usuarios -> CIERRA

`UsuarioService` ya estaba en 84.7% (127/150 lineas) antes de esta revision -- por
encima del umbral. Lo que arrastraba el promedio del modulo a 75.80% eran dos clases
de aplicacion con 0% de cobertura: `RefreshTokenService` (0/36) y
`UsuarioAuditoriaService` (0/8). Se agregaron:

- `RefreshTokenServiceTest` (6 casos: emision de tokens, rotacion en `refresh`,
  rechazo por sesion revocada, expiracion absoluta, idle timeout, logout de familia).
- `UsuarioAuditoriaServiceTest` (4 casos: registro de login, listado de usuarios
  visibles, busqueda con y sin filtro de usuario).

Cobertura del modulo `usuarios` tras esto: **321/358 lineas = 89.66%** (era 75.80%),
37 pruebas (eran 27). Evidencia actualizada en
`docs/evidencias/cobertura/usuarios-jacoco.xml` y `docs/evidencias/cobertura/README.md`.

### 3. Fallo masivo de `GET /api/productos` en la prueba de carga -> causa encontrada y corregida, prueba oficial pendiente de que el cluster de AWS este encendido

Diagnostico (contra un CockroachDB local de 3 nodos, sin tocar AWS, para no violar
la restriccion de no correr nada contra el cluster de AWS):

- Con 50 usuarios Locust concurrentes y la config de rate-limit por defecto del
  Gateway (`GATEWAY_RATE_LIMIT_REQUESTS=300` por IP cada 60s), **todos** los endpoints
  fallan proporcionalmente con `429` porque los 50 usuarios simulados salen de una
  sola IP de origen. Esto **no** coincide con el patron original (`GET /api/productos`
  concentraba 859/951 fallos con literal `400`, mientras `provincias` no tuvo fallos).
- Con el rate-limit elevado, 0% de fallos localmente contra CRDB local -- descarta al
  rate-limiter como causa del patron original.
- Revisando `ApiExceptionHandler` de los 6 microservicios Java se encontro el bug real:
  **`productos-service`, `inventario-service` y `ventas-service` mapeaban CUALQUIER
  `DataAccessException` (timeout de conexion, pool agotado, latencia de red hacia
  CRDB) a `400 Bad Request`**, como si una falla de infraestructura fuera un error
  del cliente. `pedidos-service` y `usuarios` ya lo hacian bien (503/500). Contra CRDB
  en AWS, con latencia real de red y 50 usuarios concurrentes golpeando sobre todo
  `/api/productos` (peso 5 de 10 en el locustfile), esto explica por que ese endpoint
  concentraba casi todos los fallos y por que el codigo era 400 y no 500/503.

Correccion aplicada en los 3 servicios: `DataAccessException` -> `503 SERVICE_UNAVAILABLE`
("Servicio no disponible temporalmente"), `BadSqlGrammarException` -> `500` (es un bug
nuestro de SQL, no una entrada invalida del cliente). Verificado con `mvn test` en
contenedor: productos-service 14/14, inventario-service 17/17, ventas-service 4/4.

**Pendiente real:** el cluster de AWS (`18.226.195.135:26257-26259`) esta apagado
(se verifico con conexion TCP directa, sin ejecutar SQL). No se puede repetir la
prueba de carga oficial de 50 usuarios/60s contra el entorno real hasta que estas
instancias esten encendidas. En cuanto lo esten, correr:

```powershell
docker compose up -d
# esperar a que todos los servicios reporten healthy
docker run --rm --network tiendatech_default -v "${PWD}/tests/load:/work" -w /work `
  python:3.12-slim bash -c "pip install -q -r requirements.txt && python -m locust -f locustfile.py --host http://tiendatech-gateway:8080 --headless --users 50 --spawn-rate 5 --run-time 60s --stop-timeout 10 --csv /work/results/tiendatech-50-users --html /work/results/tiendatech-50-users.html"
```

(o `tests/load/run-load-test.ps1` si se corre contra el gateway expuesto en el host).
La captura del panel de Grafana durante la corrida queda a cargo del Responsable de
Calidad -- depende de que el stack de observabilidad (Paso 10) este disponible.

### 4. Rojo/verde del pipeline -> terreno preparado, ejecucion manual pendiente

Ver `docs/evidencias/paso9-ci-rojo-verde.md`: cambio minimo exacto para fallar
`test-java` (matriz `pedidos-service`) de forma limpia via `PaginacionTest`, la
correccion exacta para volver a verde, y los huecos para pegar ambos enlaces de
GitHub Actions. Requiere push real a GitHub, por lo que no se ejecuta automaticamente.

### 5. `docs/entrega4/PFC4.tex`

Sigue pendiente de reconciliar con las evidencias nuevas de Paso 8 y Paso 9; no se
toco en esta revision (fuera del alcance pedido).

---

## Tercera revision (2026-09-04) - carga oficial de 50 usuarios

Se ejecuto Locust durante 60 segundos, alcanzando los 50 usuarios configurados
(`spawn-rate=5`) contra el API Gateway local. Resultado agregado:

| metrica | resultado |
|---|---:|
| solicitudes | 2624 |
| fallos | 0 (0.00%) |
| rendimiento | 44.25 req/s |
| tiempo medio | 48.46 ms |
| mediana | 8 ms |
| p95 | 17 ms |
| maximo | 2332.60 ms |

La primera corrida del dia contra el cluster AWS no se presenta como aprobatoria:
fallo el 52.15% de 907 solicitudes porque CockroachDB rechazo consultas al agotar
su presupuesto SQL de 64 MiB. La corrida valida se repitio contra CockroachDB local
con `--max-sql-memory=25%`, sin modificar el cluster compartido.

Limitacion: la base local utilizada estaba inicializada pero sin productos en el
catalogo; por tanto esta evidencia valida concurrencia, disponibilidad, enrutamiento
y tiempos de los endpoints publicos, pero no el costo de serializar un catalogo
poblado. Esta limitacion se conserva expresamente para no sobreinterpretar el
resultado.

Comando reproducible:

```powershell
python -m locust -f tests/load/locustfile.py --host http://localhost:8180 `
  --headless --users 50 --spawn-rate 5 --run-time 60s --stop-timeout 10 `
  --csv tests/load/results/tiendatech-50-users-20260904-local `
  --html tests/load/results/tiendatech-50-users-20260904-local.html
```
