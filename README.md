# TiendaTech — Sistema distribuido de comercio electrónico

**Asignatura:** Aplicaciones Distribuidas (ISR-701)
**Carrera:** Ingeniería de Software, séptimo semestre
**Institución:** Universidad Técnica Estatal de Quevedo (UTEQ)
**Docente:** Ing. Gleiston C. Guerrero-Ulloa, Mgs.
**Período académico:** 2026-2027
**Entrega vigente:** Entrega 4 (E4) — refactor en capas, calidad de software, aplicaciones cliente y persistencia distribuida
**Denominación anterior:** este repositorio se denominó `PFC-AppsDistribuidas` hasta la adopción de `TiendaTech` en la Entrega Final TA-PFC-E4; ambos nombres corresponden al mismo proyecto y equipo.
**Rama de trabajo:** `main` (fusionada desde `feature/entrega-4` por PR con revisión cruzada)

## Equipo

| Integrante | Rol | Usuario Git |
|---|---|---|
| Jhinson Stalyn Aucatoma Celorio | Integrante | `JhinsonAucatoma` |
| Jeremy Ruperto Gaibor Rodríguez | Integrante | `JeremyGaibor` |
| Andy Paul Sánchez Pilaloa | Integrante | `AndySanchez2004` |
| José Alejandro Lozano Morales | Integrante | `JoseLozanoMorales` |

---

## 1. Estado de la Entrega 4

El manuscrito (`docs/entrega4/PFC4.tex`) documenta el estado real del proyecto sección por sección, declarando explícitamente lo que se cumple, lo parcial y lo no implementado. Este resumen sigue ese mismo criterio: no se reporta nada como completo si no lo está.

| Frente | Alcance | Estado | Evidencia |
|---|---|---|---|
| Arquitectura en capas | Refactor de los 6 microservicios Java a `domain`/`application`/`infrastructure`/`presentation`, con patrones GoF (Repository, Factory Method, Strategy, Observer, Decorator) | ✅ Completo | `docs/entrega4/PFC4.tex` §"Arquitectura del sistema", código en `*-service/src/main/java/org/example/` |
| Persistencia distribuida | Clúster CockroachDB de 3 nodos; cada microservicio es dueño de su esquema y no consulta esquemas ajenos | ✅ Completo | `docker-compose.yml`, `.env.example`, `docs/db/schema.sql` |
| Aplicación web | SPA con 12 rutas documentadas, panel de administración completo | ✅ Completo | `docs/entrega4/PFC4.tex` §"Aplicación web", capturas en `docs/entrega4/img/` |
| Aplicación móvil | App Android con 2 capacidades de dispositivo (caché local Room/SQLite + funcionalidad adicional documentada), pruebas unitarias e instrumentadas | ✅ Completo | `docs/entrega4/PFC4.tex` §"Aplicación móvil" |
| Contratos Pact (consumidor-proveedor) | Verificación de contratos web↔backend y móvil↔backend | ⬜ No implementado | Declarado explícitamente en `docs/entrega4/PFC4.tex` §"Pruebas y CI/CD" |
| Pirámide de pruebas backend | Los 6 microservicios Java contienen pruebas; `pedidos-service` incluye integración con CockroachDB vía Testcontainers | ✅ Completo para Paso 4 | Código bajo `services/*/src/test/` |
| Pruebas de carga | Escenario Locust versionado | ✅ Implementado | `tests/load/` |
| CI/CD | `.github/workflows/ci.yml` (3 jobs: `android-mobile`, `crdb-tests`, `armado-ia-tests`) + `.github/workflows/publish-images.yml` (build multi-arquitectura de 8 imágenes) | 🟨 Parcial — cubre ~4 de los 7 jobs esperados por la rúbrica (falta `lint` Java/web dedicado y `test-web`, porque la web aún no tiene framework de pruebas configurado) | `.github/workflows/` |
| Observabilidad (OpenTelemetry, Prometheus, Grafana) | Instrumentación de métricas, logs estructurados y trazas | ⬜ No implementado, declarado fuera de alcance por decisión consciente del equipo | `docs/entrega4/PFC4.tex` §"Observabilidad" y §"Discusión" |
| Evaluación ISO/IEC 25010 | Medición formal de 5 características con intervalo de confianza 95% | ⬜ No implementado (depende de la observabilidad, que no se hizo) | `docs/entrega4/PFC4.tex` §"Evaluación ISO/IEC 25010" |
| Manuscrito completo | Introducción, arquitectura, apps web/móvil, persistencia, calidad/CI-CD, observabilidad, ISO 25010, discusión y amenazas a la validez, ética, reproducibilidad, trazabilidad E1-E4, conclusiones | ✅ Completo | `docs/entrega4/PFC4.tex` |

> **Nota de honestidad académica:** las secciones "Observabilidad", "Evaluación ISO/IEC 25010" y "Pirámide de pruebas" del manuscrito declaran sus propios vacíos con el mismo nivel de detalle que este README, en vez de reportar el proyecto como completo cuando no lo está. Ver `docs/entrega4/PFC4.tex` §"Discusión y amenazas a la validez" para la justificación de cada decisión de alcance.

### Qué está operativo hoy

| Servicio | Puerto | Persistencia | Comunicación saliente |
|---|---:|---|---|
| `tiendatech-gateway` (API Gateway) | 8180 (host) / 8080 (interno) | — (enrutador) | Enruta a los 7 microservicios |
| `productos-service` | 8081 | CockroachDB, esquema `productos` | `ventas-service` |
| `inventario-service` | 8082 | CockroachDB, esquema `inventario` | — |
| `pedidos-service` | 8083 | CockroachDB, esquema `pedidos` | `ventas-service`, `productos-service`, `usuarios-service` |
| `ordenes-proveedores-service` | 8084 | CockroachDB, esquema `ordenes_proveedores` | `inventario-service` (síncrono, Circuit Breaker) |
| `usuarios-service` | 8085 | CockroachDB, esquema `usuarios` | — |
| `ventas-service` | 8086 | CockroachDB, esquema `ventas` | `inventario-service` (asíncrono, patrón Outbox) |
| `armado-ia` (Python/FastAPI) | 8087 | — | `productos-service`, Amazon Bedrock (Nova Lite) |
| `tiendatech-crdb-1` / `tiendatech-crdb-2` / `tiendatech-crdb-3` | SQL 26257-26259, consola 8091-8093 | CockroachDB local de 3 nodos, `num_replicas = 3`, modo desarrollo sin TLS | — |

---

## 2. Requisitos previos

| Herramienta | Versión |
|---|---|
| Docker + Docker Compose v2 | Reciente, con soporte de `profiles` |
| JDK | 21 (microservicios backend); el módulo `frontend` compila con Java 17 |
| Maven | 3.9 (embebido en las imágenes de build Docker) |
| Android Studio / SDK | Para compilar la app móvil desde fuente (el CI publica el APK como artefacto) |
| LaTeX | `pdflatex` + `biblatex` (backend `biber`), paquetes `tikz`, `tabularx`, `booktabs`, `subcaption` |

---

## 3. Arranque rápido

```bash
git clone https://github.com/JoseLozanoMorales/TiendaTech.git
cd TiendaTech
```

Copie `.env.example` a `.env` y cambie los valores marcados con `reemplazar_`
(`.env` está excluido por `.gitignore`). El Compose local crea e inicializa un
clúster CockroachDB de tres nodos; no necesita certificados ni una base externa.
En producción, `CRDB_DATASOURCE_URL` y `CRDB_CERTS_DIR` deben apuntar al clúster
y certificados administrados por el equipo.

Levantar el stack completo (gateway + 7 microservicios + `armado-ia`):

```bash
cp .env.example .env
docker compose up -d --build
```

Comprobación:

```bash
docker compose ps
curl --fail http://localhost:8180/actuator/health
docker compose exec tiendatech-crdb-1 cockroach node status --insecure --host=localhost:26257
python3 scripts/audit_paso4.py
```

---

## 4. Arquitectura

El sistema se organiza como un API Gateway (Spring Cloud Gateway) que enruta hacia 7 microservicios de dominio (6 en Java/Spring Boot, 1 en Python/FastAPI para el motor de recomendación con IA), cada uno refactorizado en capas (`domain` → `application` → `infrastructure` → `presentation`) con patrones GoF aplicados según el dominio de cada servicio. La comunicación entre servicios es REST síncrona, salvo `ventas-service`→`inventario-service`, que usa un patrón Outbox transaccional asíncrono para desacoplar la generación de facturas de la disponibilidad de inventario-service.

### Diagramas disponibles (`docs/diagrams/`)

- `tiendatech-arquitectura-e4.drawio` / `.png` — Arquitectura general consolidada de la Entrega 4 (nueva, reemplaza la referencia a regenerar diagramas C4 de E3).
- `tiendatech-c4-l1.drawio` / `.png`, `tiendatech-c4-l2.drawio` / `.png`, `tiendatech-c4-l3-checkout.drawio` / `.png` — Vistas C4 heredadas de E3.
- `tiendatech-despliegue.drawio` / `.png` — Diagrama de despliegue.
- `db-schema.drawio` / `.png` — Esquema de base de datos.

---

## 5. Pruebas y CI/CD

Ver el detalle completo, con justificación de las decisiones de priorización del equipo, en `docs/entrega4/PFC4.tex` §"Pruebas y CI/CD". En resumen:

- **Con pruebas:** los seis microservicios Java (`usuarios`, `productos-service`, `inventario-service`, `pedidos-service`, `ordenes-proveedores-service` y `ventas-service`); `pedidos-service` incluye integración contra CockroachDB mediante Testcontainers.
- **CI:** `.github/workflows/ci.yml` (lint + tests + APK móvil, tests backend CRDB, tests + lint Python de `armado-ia`) y `.github/workflows/publish-images.yml` (build y publicación multi-arquitectura de las 8 imágenes en Docker Hub).
- **No implementado:** contratos Pact, pruebas E2E con Playwright y lint dedicado para todos los servicios Java y para la web. Las pruebas de carga Locust están en `tests/load/`.

---

## 6. Documentación

El manuscrito de la Entrega 4 está en `docs/entrega4/PFC4.tex`, y reutiliza la bibliografía compartida `docs/entrega3/referenciasPFC.bib`.

Compilación (desde `docs/entrega4/`):

```bash
pdflatex PFC4.tex
biber PFC4
pdflatex PFC4.tex
pdflatex PFC4.tex
```

**Advertencia:** las imágenes son relativas a `docs/entrega4/`. Para compilar desde un clon u Overleaf se necesitan `UteqLogo.png`, las imágenes utilizadas en `img/`, las dos figuras PNG de `cierre/` y la bibliografía `../entrega3/referenciasPFC.bib`, conservando esa estructura. Los diagramas C4 se generan desde TikZ. La presencia local de estos archivos no implica que estén rastreados por Git.

---

## 7. Declaración de uso de IA generativa

El equipo declara el uso de **Claude** por Jhinson y Andy, y de **Codex** por
Jeremy y José, como apoyo para análisis técnico, desarrollo, revisión y redacción.
Cada integrante conserva la responsabilidad sobre la comprobación de su aporte:
Jhinson revisó arquitectura y decisiones; Jeremy, implementación y banco de
pruebas; Andy, calidad, CI y seguridad; y José, trazabilidad, fuentes, declaraciones
y compilación documental. La declaración completa, con propósito y secciones
afectadas, está en `docs/entrega4/PFC4.tex`, sección "Declaraciones".

## 8. Paquete de reproducibilidad

El paquete experimental está formado por:

- `experiments/paso7/coordination_lab.py`: generador determinista, carga concurrente,
  inyector de fallos y oráculo de consistencia.
- `experiments/paso7/evidence/`: bancos de casos y salidas auditables del piloto.
- `experiments/paso8/run_paso8.py`: ejecución de la matriz, análisis estadístico y
  generación de figuras SVG.
- `experiments/paso8/resultados/experimento_crudo.csv`: 120 corridas crudas, junto
  con resúmenes, bases SQLite y metadatos.
- `experiments/paso8/analisis.ipynb`: cuaderno de inspección independiente.
- `experiments/paso8/execute_notebook.py`: ejecutor verificable del cuaderno sin
  dependencias adicionales.
- `docs/entrega4/cierre/generar_figuras.py`: regeneración de las figuras rasterizadas
  del documento desde los CSV conservados.
- `CITATION.cff`: autoría y forma de citar el software.

Versiones de referencia: CPython 3.11.1, Matplotlib 3.9.0 (fijado en
`experiments/requirements.txt`), TeX Live 2026 y Biber. El ejecutor experimental
solo usa la biblioteca estándar. Desde una clonación limpia, los comandos exactos
para comprobar el banco, regenerar resultados y figuras, ejecutar el cuaderno y
compilar el PDF están en `docs/entrega4/README.md`.

---

## 9. Trazabilidad con la rúbrica

> **Cierre acumulativo del Paso 13 (1 de septiembre de 2026):** la versión actualizada es [PFC4.tex](docs/entrega4/PFC4.tex), con [PDF](docs/entrega4/PFC4.pdf) e [instrucciones de compilación con Biber](docs/entrega4/README.md). Las tablas históricas de esta sección no sustituyen el diagnóstico actualizado de esa memoria, que incorpora las evidencias posteriores y sus límites.

Ver `docs/entrega4/PFC4.tex` §"Trazabilidad E1-E4" para la tabla completa de cierre del ciclo de las 4 entregas, y la Tabla 4 de `RÚBRICA FINAL PFC.pdf` (dimensiones D1-D9) para los pesos exactos de evaluación. Resumen por dimensión:

| Dimensión | Estado |
|---|---|
| D1 — Arquitectura y decisiones | ✅ Completo |
| D2 — Aplicación web | ✅ Completo |
| D3 — Aplicación móvil | ✅ Completo |
| D4.1 — Contratos Pact | ⬜ No implementado |
| D4.2 — Persistencia distribuida | ✅ Completo (clúster de 3 nodos y propiedad por esquema) |
| D5.1 — Pirámide de pruebas | 🟨 Parcial (4/6 microservicios Java, sin E2E ni carga) |
| D5.2 — Pipeline CI/CD | 🟨 Parcial (~4/7 jobs esperados) |
| D6 — Observabilidad | ⬜ No implementado, fuera de alcance declarado |
| D7 — Evaluación ISO/IEC 25010 | ⬜ No implementado, fuera de alcance declarado |
| D8 — Documentación y reproducibilidad | ✅ Completo |
| D9 — Ética, discusión y defensa oral | ✅ Completo (defensa oral pendiente de presentar) |

---

## 10. Pendientes conocidos

- Contratos Pact (consumer-driven) entre clientes web/móvil y backend: no iniciados.
- Falta ampliar la cobertura de `ordenes-proveedores-service` y `ventas-service`; ambos ya contienen pruebas unitarias.
- No hay E2E con Playwright; sí existe el escenario de carga Locust en `tests/load/`.
- Pipeline CI/CD cubre parcialmente los 7 jobs esperados por la rúbrica; falta `lint` dedicado para servicios Java y para la web (la web no tiene framework de pruebas configurado todavía).
- Observabilidad distribuida (OpenTelemetry, Prometheus, Grafana) y evaluación ISO/IEC 25010: declaradas explícitamente fuera de alcance de esta entrega por restricción de tiempo del equipo — ver justificación completa en `docs/entrega4/PFC4.tex` §"Discusión".
- Para producción deben sustituirse todos los valores de ejemplo y montarse los certificados del clúster administrado; el Compose local es autocontenido y no requiere sobrescribir `CRDB_DATASOURCE_URL`.
# Paso 3 — TCP, gRPC y relojes de Lamport

El carrito reserva stock mediante un canal TCP persistente entre `pedidos-service`
y `inventario-service`. Cada mensaje usa un encabezado de 4 bytes, entero sin
signo en orden de red (big-endian), seguido por exactamente esa cantidad de bytes
JSON. Tanto cliente como servidor leen en un ciclo (`readFully`); nunca asumen que
un solo `recv` contiene el mensaje completo. Inventario publica la misma operación
en gRPC para el experimento comparativo.

El contrato fuente versionado es `contracts/stock_reservation.proto`. Maven genera
Java dentro de `target/generated-sources/protobuf`, que está ignorado y no debe
subirse. Para regenerar y compilar:

```bash
mvn -f services/inventario-service/pom.xml clean compile
mvn -f services/pedidos-service/pom.xml clean compile
```

Los stubs Python del experimento también son temporales:

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r experiments/requirements.txt
sh experiments/generate_proto.sh
python experiments/run_latency.py --host 127.0.0.1 \
  --user-id 47 --product-id 4 --tcp-cart-id 1001 --grpc-cart-id 1002
```

El ejecutor usa `time.perf_counter()` y realiza 100 envíos por tecnología de
forma predeterminada. Escribe cada observación en
`experiments/data/latency_sockets.csv` y `latency_grpc.csv`, muestra media,
mediana, desviación estándar y percentil 95, y genera
`experiments/figures/latency_boxplot.png` a 300 DPI. Los identificadores deben
existir en la base desplegada; los CSV incluidos solo contienen la cabecera hasta
ejecutar el experimento real y no constituyen resultados fabricados.

### Compilar el documento acumulativo

Desde la raíz del repositorio, con TeX Live y Biber:

```powershell
cd docs/entrega4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
biber PFC4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
```

La bibliografía es `docs/entrega3/referenciasPFC.bib`. Para compilar desde un clon también deben versionarse el logo y las figuras utilizadas; los archivos v2 no son necesarios.
