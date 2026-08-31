# Bitácora del proyecto — TiendaTech

## Paso 0 — Congelar el punto de partida (Entrega Final, TA-PFC-E4)

**Fecha:** 2026-08-31

**Etiqueta:** `pre-e4`

**Commit congelado (SHA completo):** `b24a571c88705604f4c48845f61930e8142dc3d7`
*(verificado como `HEAD` de `main` en el remoto público `github.com/JoseLozanoMorales/TiendaTech` mediante `git ls-remote` el 2026-08-31; 

**Repositorio público y accesible de forma anónima:** verificado.
```
$ GIT_TERMINAL_PROMPT=0 git ls-remote https://github.com/JoseLozanoMorales/TiendaTech HEAD
b24a571c88705604f4c48845f61930e8142dc3d7	HEAD
```

**LICENSE:** ausente al momento de este corte — se añade en este mismo commit (MIT, ver `/LICENSE`).

**.gitignore:** presente.

### Estado honesto del sistema a la fecha de este corte

**Funciona, verificado:**

- Los seis microservicios Java (`inventario-service`, `productos-service`, `ordenes-proveedores-service`, `ventas-service`, `pedidos-service`, `usuarios`) tienen pruebas unitarias reales con JUnit 5 y superan el gate de cobertura JaCoCo ≥70 %.
- `armado-ia` (Python) supera `pytest-cov --cov-fail-under=70`.
- El pipeline `.github/workflows/ci-cd.yml` corre seis jobs (`test-java`, `test-python`, `lint`, `integration-test`, `build`, `coverage`) y todos están en verde.
- `GatewayIntegrationTest` (11 pruebas): tres flujos reales a través del API Gateway (catálogo, ubicaciones, creación de pedidos) más verificación de `401` sin token JWT en las ocho familias de rutas protegidas.
- `CockroachDbIntegrationTest`: prueba de integración real contra un contenedor CockroachDB vía Testcontainers.
- El clúster CockroachDB de tres nodos opera con réplica Raft; hay evidencia documentada de una prueba de tolerancia a fallos (`docs/evidencias/tolerancia_fallos.md`).
- Codecov recibe y publica los reportes de cobertura reales (subida confirmada con `status_code=200`; 75.93 % agregado sobre `main` en el commit `dd72b3d4`).
- El documento `docs/entrega4/PFC4.tex` compila sin errores a PDF (42 páginas, sin referencias rotas).

**No funciona o está incompleto, honestamente:**

- No hay *consumer-driven contract testing* con Pact (D4.1): sin carpeta `tests/contract`, sin dependencia `pact-jvm`. Declarado explícitamente como no cumplido en `PFC4.tex`.
- Observabilidad (D6 — OpenTelemetry, métricas Prometheus, logs estructurados con `trace_id`, dashboard Grafana): no implementada en el código de los servicios. Confirmado: sigue sin implementar; la corrida del 28/08 no la tocó (ver punto siguiente).
- Evaluación ISO/IEC 25010:2023 (D7) — **actualizado tras revisar `docs/experimentos/resultados/iso25010/` (corrida oficial del equipo, 28/08, responsable Jeremy según `docs/experimentos/protocolo-iso25010.md`):**
  - **Mantenibilidad — cobertura:** `CUMPLE` (sin cambios, ≥70 % en los seis servicios Java; `armado-ia` declarado por el equipo, pendiente adjuntar su `coverage.xml`).
  - **Mantenibilidad — complejidad ciclomática:** **recién medida con PMD 7.17.0 — `NO CUMPLE`.** Máximo por método: 20 (objetivo `<10`). Cinco métodos violan el umbral: `JdbcInventarioRepository.registrarItem` (20), `UsuarioService.cambiarPasswordConToken` (17), `OrdenService.generarOrdenDesdeCarrito` (15), `JdbcProductoRepository.agregarImagen` (14), `JdbcOrdenCompraRepository.validarDetalle` (11). **Esto es nuevo respecto de `PFC4.tex`, que todavía dice "no se ha medido"** — hay que actualizar esa sección y, sobre todo, refactorizar esos cinco métodos antes del cierre.
  - **Seguridad:** `CUMPLE` (sin cambios, 8/8 familias, 11 casos, 0 fallos) — reconfirmado, no es información nueva.
  - **Disponibilidad, Rendimiento, Fiabilidad: siguen `PENDIENTE`, genuinamente.** La corrida del 28/08 se hizo en un entorno sin *daemon* Docker disponible (`ejecucion-2026-08-28.md` lo declara explícitamente), así que la prueba de uptime de 3600 s nunca arrancó. Sí existe un CSV de Locust suelto en `local-2026-08-28T23-31-00/` (fuera del protocolo oficial: nombre de carpeta no estándar, y los datos muestran P95 agregado de 38 000 ms y 34/68 fallos — un stack degradado, no una medición válida), y el equipo hizo bien en **no** usarlo para llenar `iso25010.csv`. Falta correr el protocolo oficial completo (`docs/experimentos/protocolo-iso25010.md`) en una máquina con Docker: Locust 50 usuarios/60 s + ventana de disponibilidad de 3600 s.
- El workflow legado `.github/workflows/ci.yml` sigue activo en paralelo a `ci-cd.yml`; su consolidación en un único pipeline sigue pendiente.
- No existe todavía carpeta `tests/` en la raíz para pruebas end-to-end (Playwright) ni pruebas de carga con Locust integradas al pipeline (más allá de la corrida local mencionada arriba).

