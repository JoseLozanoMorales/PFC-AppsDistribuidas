# TiendaTech — Sistema distribuido de comercio electrónico

**Asignatura:** Aplicaciones Distribuidas (ISR-701)
**Carrera:** Ingeniería de Software, séptimo semestre
**Institución:** Universidad Técnica Estatal de Quevedo (UTEQ)
**Docente:** Ing. Gleiston C. Guerrero-Ulloa, Mgs.
**Período académico:** 2026-2027
**Entrega vigente:** Entrega 3 (E3) — clúster de datos distribuido, tolerancia a fallos y pipeline analítico paralelo
**Rama de trabajo:** `feature/entrega-3`

## Equipo

| Integrante | Rol | Usuario Git |
|---|---|---|
| Jhinson Stalyn Aucatoma Celorio | Integrante | `JhinsonAucatoma` |
| Jeremy Ruperto Gaibor Rodríguez | Integrante | `JeremyGaibor` |
| Andy Paul Sánchez Pilaloa | Integrante | `AndySanchez2004` |
| José Alejandro Lozano Morales | Integrante | `JoseLozanoMorales` |

<!-- TODO: verificar — el equipo debe confirmar el rol específico de cada integrante (p. ej. líder, responsable de capa de datos, responsable de pipeline); no se encontró esa asignación documentada en el repositorio. -->

---

## 1. Estado de la Entrega 3

La numeración A–G agrupa los dieciséis criterios de la rúbrica (sección 13) en
siete bloques temáticos, para facilidad de lectura. No corresponde a una
nomenclatura oficial de la guía; es un ordenamiento propio hecho a partir de
la evidencia encontrada en `docs/evidencias/`, `docs/adr/` y `docs/db/`.

| Módulo | Alcance (criterios rúbrica) | Estado | Evidencia |
|---|---|---|---|
| A — Esquema distribuido y fragmentación | 1.1, 1.2 | ✅ Completo | `docs/adr/ADR-003-fragmentacion-pedidos.md`, `docs/db/schema.sql`, `docs/evidencias/validacion-esquema-distribuido.md` |
| B — Clúster CockroachDB de 3 nodos | 2.1 | ✅ Completo | `docs/evidencias/cluster-cockroachdb-3-nodos.md` |
| C — Tolerancia a fallos | 2.2 | 🟨 Parcial | `docs/evidencias/tolerancia_fallos.md` + `docs/evidencias/resultados-tolerancia/`; falta el vídeo exigido por la guía |
| D — Integración microservicio↔clúster y métricas | 3.1, 3.2 | 🟨 Parcial | `docs/evidencias/integracion-pedidos-crdb.md`, `docs/evidencias/colision-serializable-controlada.md`; corre bajo el perfil `e3-crdb`, no reemplaza aún el flujo por defecto |
| E — Pipeline analítico paralelo (PySpark) | 4.1 | ✅ Completo | `spark/pipeline.py`, `spark/baseline.py`, `docs/evidencias/resultados-pipeline-analitico.md` |
| F — Protocolo experimental y comparativa | 4.2, 4.3 | ✅ Completo | `docs/experimentos/protocolo.md`, `docs/evidencias/resultados-pipeline-analitico.md` |
| G — Documentación, trazabilidad y reproducibilidad | 5.x, 6.x | 🟨 Parcial | `docs/entrega3/PFC3.tex`, este README; ver discrepancia señalada abajo |

> **Discrepancia detectada:** la sección "Resultados de la Entrega 3" del
> manuscrito (`docs/entrega3/PFC3.tex`, líneas 1094–1188) describe únicamente
> el flujo heredado sobre PostgreSQL mono-nodo (puertos 8080–8086) y no
> incorpora los resultados del clúster CockroachDB, la prueba de tolerancia a
> fallos ni el pipeline PySpark, pese a que esa evidencia sí existe en
> `docs/evidencias/` y `docs/experimentos/`. El manuscrito todavía no está
> sincronizado con el trabajo de datos distribuidos y analítica realizado.

### Qué está operativo hoy

| Servicio | Puerto | Persistencia | Requiere perfil |
|---|---:|---|---|
| `frontend` | 8080 | — (enrutador) | No |
| `productos-service` | 8081 | PostgreSQL mono-nodo (`TiendaTechV19`) | No |
| `inventario-service` | 8082 | PostgreSQL mono-nodo | No |
| `pedidos-service` | 8083 | PostgreSQL mono-nodo | No |
| `ordenes-proveedores-service` | 8084 | PostgreSQL mono-nodo | No |
| `usuarios-service` | 8085 | PostgreSQL mono-nodo | No |
| `ventas-service` | 8086 | PostgreSQL mono-nodo | No |
| `crdb-1` / `crdb-2` / `crdb-3` | SQL 26257 / 26258 / 26259 · consola 8091 / 8092 / 8093 | CockroachDB, `num_replicas = 3` | `e3-crdb` |
| `pedidos-crdb-service` | 8183 | CockroachDB (clúster anterior) | `e3-crdb` |
| `ventas-crdb-service` | 8186 | CockroachDB (clúster anterior) | `e3-crdb` |

**Aviso de alcance:** el stack que arranca por defecto (`docker compose up`)
sigue sobre **PostgreSQL mono-nodo compartido**, tal como lo reconoce el
propio manuscrito en su sección de limitaciones. El clúster CockroachDB de
tres nodos y los servicios `pedidos-crdb-service` / `ventas-crdb-service` que
sí lo usan solo se levantan con el perfil `e3-crdb` y coexisten con el stack
por defecto sin reemplazarlo todavía.

---

## 2. Requisitos previos

| Herramienta | Versión usada/verificada en el repositorio |
|---|---|
| Docker | <!-- TODO: verificar — no se encontró una versión mínima documentada; usar una versión reciente con soporte de `profiles` en Compose (2021+). --> |
| Docker Compose | v2 (sintaxis `profiles` en `docker-compose.yml`) |
| JDK | 21 (todos los microservicios backend); el módulo `frontend` compila con Java 17 |
| Maven | 3.9 (usado en las pruebas de integración registradas en `docs/evidencias/integracion-pedidos-crdb.md`); cada módulo incluye su propio `mvnw`/`mvnw.cmd` |
| Python | 3.x compatible con PySpark 3.5.5 (la imagen `spark/Dockerfile` usa `apache/spark:3.5.5-python3`); ver `spark/requirements.txt` para las versiones exactas de librerías |
| LaTeX | Distribución con `pdflatex`, `bibtex` y los paquetes `biblatex` (estilo `ieee`, backend `bibtex`), `tikz`, `tabularx`, `booktabs` |

---

## 3. Arranque rápido

```bash
git clone <url-del-repositorio>
cd PFC-AppsDistribuidas
git checkout feature/entrega-3
```

**Variables de entorno:** el repositorio no incluye un `.env.example`. Existe
un `.env` en la raíz con credenciales de PostgreSQL y el secreto JWT, pero
**no está protegido por `.gitignore`** (ver [Pendientes conocidos](#15-pendientes-conocidos)).
Antes de continuar, cree su propio `.env` en la raíz con, como mínimo:

```dotenv
TT_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/TiendaTechV19
TT_DATASOURCE_USERNAME=postgres
TT_DATASOURCE_PASSWORD=<su_password>
AUTH_JWT_SECRET=<su_secreto>
TT_MAIL_USERNAME=<opcional>
TT_MAIL_PASSWORD=<opcional>
COOKIE_SECURE=false
```

Levantar el stack por defecto (frontend + seis microservicios sobre
PostgreSQL mono-nodo):

```bash
docker compose up --build
```

Comprobación de los servicios:

```bash
curl http://localhost:8080/actuator/health   # frontend
curl http://localhost:8081/actuator/health   # productos-service
curl http://localhost:8082/actuator/health   # inventario-service
curl http://localhost:8083/actuator/health   # pedidos-service
curl http://localhost:8084/actuator/health   # ordenes-proveedores-service
curl http://localhost:8085/actuator/health   # usuarios-service
curl http://localhost:8086/actuator/health   # ventas-service
```

Levantar además el clúster CockroachDB de tres nodos y los servicios que lo
usan (perfil `e3-crdb`, definido en `docker-compose.yml`):

```bash
docker compose --profile e3-crdb up -d crdb-1 crdb-2 crdb-3 crdb-init crdb-seed pedidos-crdb-service ventas-crdb-service
docker exec tiendatech-crdb-1 cockroach node status --insecure --host=localhost:26257
curl http://localhost:8183/health   # pedidos-crdb-service
curl http://localhost:8186/health   # ventas-crdb-service
```

<!-- TODO: verificar — no se encontró en el repositorio un endpoint /actuator/health explícito confirmado por captura para cada servicio del stack por defecto; se asume por ser Spring Boot con starter-actuator declarado en los pom.xml. Confirmar antes de publicar. -->

---

## 4. Arquitectura

El sistema se organiza como un frontend (Spring Cloud Gateway) que enruta
hacia seis microservicios de dominio, cada uno con su propio módulo Maven:
`productos-service`, `inventario-service`, `pedidos-service`,
`ordenes-proveedores-service`, `usuarios` (imagen `usuarios-service`) y
`ventas-service`. La comunicación entre servicios es REST síncrona (por
ejemplo, `pedidos-service` consulta `ventas-service`, `productos-service` y
`usuarios-service`; `ventas-service` consulta `inventario-service`). Todos los
contenedores comparten la red por defecto de Compose; los servicios del
perfil `e3-crdb` usan además la red interna `pfc-net`.

Existe además, en `src/` (raíz del repositorio), un proyecto Spring Boot
independiente (`com.example.tienda_tech`) con vistas estáticas HTML. No tiene
`Dockerfile` propio ni aparece en `docker-compose.yml`, por lo que no forma
parte del despliegue distribuido activo.
<!-- TODO: verificar — confirmar con el equipo si `src/` es un remanente de una entrega previa (monolito original) que debe documentarse como legado o si debe eliminarse. -->

### Diagramas disponibles (`docs/diagrams/`)

- `tiendatech-c4-l1.drawio` / `.png` — Contexto del sistema (C4 nivel 1).
- `tiendatech-c4-l2.drawio` / `.png` — Contenedores (C4 nivel 2).
- `tiendatech-c4-l3-checkout.drawio` / `.png` — Componentes del flujo de checkout (C4 nivel 3).
- `tiendatech-despliegue.drawio` / `.png` — Diagrama de despliegue.
- `db-schema.drawio` / `.png` — Esquema de base de datos.

---

## 5. Capa de datos distribuida

El módulo B (`pedidos-crdb-service`, `ventas-crdb-service`) persiste sobre un
clúster CockroachDB 23.2.4 de tres nodos (`crdb-1`, `crdb-2`, `crdb-3`,
perfil `e3-crdb`), inicializado con `docs/db/schema.sql` y poblado con
`docs/db/seeds.sql`.

- **Fragmentación horizontal:** `pedidos.orden` y `pedidos.detalle_orden` se
  fragmentan por rangos trimestrales de `fecha` (equivalente a `fecha_pedido`
  en la guía), materializados con `SPLIT AT` y distribuidos con `SCATTER` —
  CockroachDB Community (23.2.4) rechaza `PARTITION BY RANGE` por ser una
  capacidad Enterprise. Decisión documentada en
  `docs/adr/ADR-003-fragmentacion-pedidos.md`.
- **Colocalización `clientes ⋉ pedidos`:** se resuelve mediante el índice
  `(usuario_id, fecha DESC)` sobre `pedidos.orden`, como afinidad de acceso;
  `INTERLEAVE IN PARENT` fue descartado por estar retirado de versiones
  modernas de CockroachDB.
- **Replicación:** `num_replicas = 3` en la configuración de zona por
  defecto, confirmado en `docs/evidencias/cluster-cockroachdb-3-nodos.md`
  (`replicas = {1,2,3}`, `voting_replicas = {1,2,3}`). Decisión de consenso
  documentada en `docs/adr/ADR-004-consenso-raft.md`.

Verificación:

```bash
docker exec tiendatech-crdb-1 cockroach node status --insecure --host=localhost:26257
```

---

## 6. Tolerancia a fallos

Procedimiento previsto y ya ejecutado una vez, registrado en
`docs/evidencias/tolerancia_fallos.md` y
`docs/evidencias/resultados-tolerancia/` (`mediciones.csv`, `resumen.csv`,
capturas de estado):

1. Confirmar los tres nodos vivos y ejecutar 5 repeticiones de una consulta
   de control con los tres nodos disponibles.
2. Detener `crdb-2` con `docker kill` y repetir la consulta 5 veces (queda
   quórum con 2 réplicas).
3. Reincorporar `crdb-2` y repetir la consulta 5 veces.
4. Detener simultáneamente `crdb-2` y `crdb-3` (una sola réplica, sin
   quórum) y ejecutar la consulta con `statement_timeout` de 8 s.
5. Restaurar los nodos y confirmar `is_available=true` / `is_live=true` en
   los tres.

Script: `docs/evidencias/probar-tolerancia-fallos.ps1`.

**Evidencia esperada pendiente:** el vídeo de la caída controlada de uno y
dos nodos, exigido por la guía, todavía no se ha grabado — así lo señala
explícitamente `docs/evidencias/tolerancia_fallos.md` en su sección de
limitaciones. La bitácora escrita y los datos crudos (CSV) sí están
disponibles.

---

## 7. Pipeline analítico paralelo

Ubicado en `spark/`:

- `pipeline.py` — pipeline PySpark.
- `baseline.py` — equivalente en pandas.
- `experimento.py` — orquesta repeticiones y calcula estadísticos.
- `validar_resultados.py` — compara igualdad numérica pandas/PySpark.
- `requirements.txt` — dependencias (`pandas`, `pyspark`, `pyarrow`, `scipy`, `psycopg`, `psutil`).
- `analisis.ipynb` — notebook de análisis.
- `ejecutar-pyspark.ps1` — ejecuta PySpark en Windows usando la imagen Linux oficial de Spark (evita depender de `winutils.exe`).

Cinco transformaciones exigidas, implementadas de forma equivalente en ambos
motores (`docs/experimentos/protocolo.md`):

1. **Filtrado** — año 2026, órdenes no canceladas, cantidades positivas, usuarios habilitados.
2. **Join** entre tablas colocalizadas — orden, detalle y usuario.
3. **Agregación con ventanas** — unidades por producto y ranking top-10 por trimestre; frecuencia y gasto por cliente.
4. **Transformación de tipos temporales** — conversión de `fecha` y derivación de trimestre.
5. **Operación de `spark.ml`** — `Bucketizer` de gasto en segmentos BRONCE/PLATA/ORO/PLATINO.

Salida en Parquet: `top_productos`, `segmentos_clientes`.

Resultados ya obtenidos sobre un dataset determinista de 600 000 órdenes y
600 000 detalles están en `docs/evidencias/resultados-pipeline-analitico.md`.

---

## 8. Protocolo experimental

Definido en `docs/experimentos/protocolo.md` y ya ejecutado una vez
(`docs/evidencias/resultados-pipeline-analitico.md`):

- `N ∈ {1, 2, 4, 8}` workers PySpark en modo `local[N]`, más un baseline pandas.
- `r = 10` repeticiones por configuración, descartando la primera y la última
  (8 mediciones válidas por configuración).
- Media, desviación típica e intervalo de confianza al 95 % sobre las 8
  mediciones restantes.
- Prueba de normalidad Shapiro-Wilk sobre las diferencias emparejadas; si
  `p ≥ 0.05` se aplica prueba t pareada, en caso contrario Wilcoxon.

Comando de referencia:

```powershell
python spark/baseline.py --overwrite
powershell -ExecutionPolicy Bypass -File spark/ejecutar-pyspark.ps1 -Workers 4 -Salida pyspark
python spark/validar_resultados.py
python spark/experimento.py --repeticiones 10 --workers 1 2 4 8 --incluir-pandas
```

---

## 9. Estructura del repositorio

```text
.
├── docker-compose.yml
├── docs/
│   ├── adr/                  # ADR-003 (fragmentación), ADR-004 (consenso Raft)
│   ├── db/                   # schema.sql, seeds.sql (CockroachDB)
│   ├── diagrams/             # Diagramas C4 (L1–L3), despliegue, esquema DB
│   ├── entrega1/             # entrega1.pdf
│   ├── entrega2/             # entrega2.pdf
│   ├── entrega3/             # PFC3.tex, referenciasPFC.bib (manuscrito E3)
│   ├── evidencias/           # Evidencia verificable de clúster, tolerancia, integración, pipeline
│   └── experimentos/         # protocolo.md
├── frontend/                 # Spring Cloud Gateway (puerto 8080)
├── inventario-service/       # Puerto 8082
├── ordenes-proveedores-service/  # Puerto 8084
├── pedidos-service/          # Puerto 8083 (también origen de pedidos-crdb-service)
├── productos-service/        # Puerto 8081
├── spark/                    # Pipeline PySpark, baseline pandas, experimento
├── src/                      # Proyecto Spring Boot independiente, sin Dockerfile ni referencia en compose (legado, ver §4)
├── usuarios/                 # Imagen usuarios-service, puerto 8085
└── ventas-service/           # Puerto 8086 (también origen de ventas-crdb-service)
```

---

## 10. Documentación

El manuscrito de la Entrega 3 está en `docs/entrega3/PFC3.tex`, con su
bibliografía en `docs/entrega3/referenciasPFC.bib`.

Compilación (desde `docs/entrega3/`):

```bash
pdflatex PFC3.tex
bibtex PFC3
pdflatex PFC3.tex
pdflatex PFC3.tex
```

**Advertencias:**

- Las rutas de las figuras (`\includegraphics{../diagrams/...}`) son
  relativas a `docs/entrega3/`. Si se compila en Overleaf, es necesario subir
  también la carpeta `docs/diagrams/` con esa misma estructura relativa, o la
  compilación fallará al no encontrar las imágenes.
- El documento usa `backend=bibtex` (no `biber`), que no maneja UTF-8 de
  forma fiable. Por eso los acentos en `referenciasPFC.bib` están escapados
  en notación LaTeX (por ejemplo, `{\"O}zsu`) en las entradas originales del
  eje de distribución; **no deben convertirse a UTF-8 directo**, o `bibtex`
  puede fallar o producir caracteres incorrectos en el PDF.

---

## 11. Declaración de uso de IA generativa

*(Pendiente de completar por el equipo — no autocompletada por esta
generación de README)*

| Herramienta | Propósito | Alcance |
|---|---|---|
| | | |

---

## 12. Trazabilidad con la rúbrica

| Criterio | Peso | Evidencia prevista | Estado |
|---|---:|---|---|
| 1.1 Esquema distribuido y fragmentación | 9 % | `docs/adr/ADR-003-fragmentacion-pedidos.md`, `docs/db/schema.sql`, `docs/evidencias/validacion-esquema-distribuido.md` | ✅ Completo |
| 1.2 Diseño de replicación y factor Raft | 8 % | `docs/adr/ADR-004-consenso-raft.md`, `docs/evidencias/cluster-cockroachdb-3-nodos.md` | ✅ Completo |
| 2.1 Clúster de 3 nodos funcionando | 8 % | `docs/evidencias/cluster-cockroachdb-3-nodos.md` | ✅ Completo |
| 2.2 Verificación de tolerancia a fallos | 10 % | `docs/evidencias/tolerancia_fallos.md`, `docs/evidencias/resultados-tolerancia/` | 🟨 Parcial — falta vídeo |
| 3.1 Integración microservicio → clúster | 8 % | `docs/evidencias/integracion-pedidos-crdb.md` | 🟨 Parcial — bajo perfil `e3-crdb`, no reemplaza el stack por defecto |
| 3.2 Métricas Prometheus incrementales | 4 % | `docs/evidencias/integracion-pedidos-crdb.md`, `docs/evidencias/colision-serializable-controlada.md` | 🟨 Parcial — contadores validados con `promtool`, sin servidor Prometheus desplegado en `docker-compose.yml` |
| 4.1 Pipeline PySpark completo | 10 % | `spark/pipeline.py`, `docs/evidencias/resultados-pipeline-analitico.md` | ✅ Completo |
| 4.2 Baseline pandas y comparativa | 4 % | `spark/baseline.py`, `docs/evidencias/resultados-pipeline-analitico.md` | ✅ Completo |
| 4.3 Protocolo experimental | 8 % | `docs/experimentos/protocolo.md`, `docs/evidencias/resultados-pipeline-analitico.md` | ✅ Completo |
| 5.1 Estructura del manuscrito | 5 % | `docs/entrega3/PFC3.tex` | 🟨 Parcial — sección de resultados no integra evidencia CRDB/tolerancia/Spark (ver §1) |
| 5.2 Uso de LaTeX y calidad tipográfica | 4 % | `docs/entrega3/PFC3.tex` | 🟨 Parcial — ver pendientes de compilación en §14 |
| 5.3 Bibliografía IEEE | 4 % | `docs/entrega3/referenciasPFC.bib` | 🟨 Parcial — entradas duplicadas, ver §14 |
| 6.1 Repositorio y trazabilidad | 4 % | Este README, `docs/adr/`, `docs/evidencias/` | 🟨 Parcial |
| 6.2 Reproducibilidad | 4 % | `docker-compose.yml`, este README | 🟨 Parcial — falta `.env.example`; `.env` no ignorado correctamente |
| 7.1 Defensa oral | 6 % | ⬜ Pendiente | ⬜ Pendiente |
| 7.2 Ética y honestidad académica | 4 % | Sección 11 (declaración de IA, pendiente de completar por el equipo) | ⬜ Pendiente |

---

## 13. Aclaraciones: contenido adicional a la rúbrica

Esta sección distingue lo exigido por la guía de la Entrega 3 de lo construido
por encima de ella. Nada de lo aquí listado sustituye un requisito de la
rúbrica.

- **Seis microservicios en lugar de uno.** La guía pide refactorizar el
  microservicio principal hacia el clúster distribuido; este proyecto
  mantiene seis dominios separados (`productos`, `inventario`, `pedidos`,
  `ordenes-proveedores`, `usuarios`, `ventas`), por lo que la superficie de la
  refactorización es mayor que la prevista.
- **Tres niveles de diagramas C4 más despliegue.** La guía pide los niveles 2
  y 3; el repositorio incluye además el nivel 1 (`tiendatech-c4-l1.drawio`) y
  un diagrama de despliegue (`tiendatech-despliegue.drawio`).
- **ADR heredados.** Además de `ADR-003-fragmentacion-pedidos.md` y
  `ADR-004-consenso-raft.md`, que exige E3, el manuscrito conserva ADR-01
  (arquitectura de microservicios), ADR-02 (enrutamiento centralizado) y
  ADR-03 (motor de compatibilidad, propuesto y no implementado) de entregas
  anteriores.
- **Métricas de calidad ISO/IEC 25010.** El manuscrito define umbrales
  (P95, throughput, uptime, error rate, MTTR, etc.) que E3 no exige; se
  conservan porque alimentan la Entrega 4.
- **Validación funcional y de latencia REST.** Diez casos funcionales y 210
  solicitudes secuenciales sobre siete endpoints, con P95 máximo de 51,94 ms
  (`docs/entrega3/PFC3.tex`, sección "Resultados de la Entrega 3"). Esto **no**
  es el protocolo experimental del criterio 4.3: aquello mide latencia HTTP de
  la capa de servicios sobre PostgreSQL mono-nodo, mientras el criterio 4.3
  exige medir aceleración del pipeline paralelo con repeticiones e intervalos
  de confianza. Son mediciones distintas y se reportan por separado.
- **Dos ejes en trabajos relacionados.** La guía pide al menos cinco fuentes
  indexadas sobre bases de datos distribuidas, CockroachDB, Spark y la ley de
  Amdahl; el manuscrito cubre ese eje (nueve fuentes del período 2020–2024,
  según su propio texto) y conserva además el eje heredado sobre sistemas de
  recomendación y configuración por restricciones.
- **Fuentes primarias fuera del rango de años.** Amdahl (1967), Gustafson
  (1988) y Zhou et al. (2008) se citan pese a ser anteriores al período de
  revisión, porque la atribución de un método exige citar su formulación
  original.

---

## 14. Pendientes conocidos

- Las figuras del manuscrito no se resuelven al compilar en Overleaf, porque
  `docs/diagrams/` no está subido allí (ver §10).
- Bibliografía con entradas duplicadas: `ozsu2020`/`ozsu2020principles`,
  `taft2020`/`taft2020cockroachdb`, `amdahl1967`/`amdahl1967validity` y
  `gustafson1988`/`gustafson1988reevaluating` aparecen dos veces con claves
  distintas en `docs/entrega3/referenciasPFC.bib`; la segunda entrada de Taft
  está además malformada.
- La cadena `artno` aparece sin traducir en la referencia de Ahmed et al.
  (2021) (`docs/entrega3/referenciasPFC.bib`, entrada `ahmed2021`, campo
  `eid = {107}`), por localización española del estilo IEEE.
- El contador de página se reinicia tras el entorno `titlepage`
  (`docs/entrega3/PFC3.tex`, líneas 127–183), lo que produce dos páginas
  numeradas 1 y anclajes duplicados en el PDF.
- Contratos OpenAPI no formalizados (deuda técnica declarada desde la
  Entrega 2).
- Aislamiento de persistencia por servicio no implementado: el stack por
  defecto comparte una única base PostgreSQL (`TiendaTechV19`) entre los seis
  microservicios.
- La sección "Resultados de la Entrega 3" del manuscrito no refleja todavía
  el trabajo sobre CockroachDB, tolerancia a fallos ni el pipeline PySpark
  (ver discrepancia en §1).
- Falta grabar el vídeo de la caída controlada de uno y dos nodos, exigido
  por la guía (criterio 2.2); la bitácora y los datos crudos sí existen.
- No existe `.env.example`; el repositorio contiene un `.env` real con
  credenciales (`TT_DATASOURCE_PASSWORD`, `AUTH_JWT_SECRET`).
- El `.gitignore` de la raíz está codificado en UTF-16, que Git no interpreta
  como lista de patrones válida (`git check-ignore -v .env` no reporta
  ninguna coincidencia). En la práctica, **`.env` no está ignorado** pese a
  aparecer listado en el archivo, y quedó como archivo sin seguimiento (`??`)
  en `git status` en vez de ser excluido. Debe corregirse la codificación del
  `.gitignore` a UTF-8 antes de que alguien lo agregue por accidente.
- No existe `.github/workflows/`: no hay integración continua configurada.
- El servicio `pedidos-crdb-service` expone contadores Prometheus
  incrementales validados con `promtool`, pero no hay un servidor Prometheus
  ni Grafana desplegado en `docker-compose.yml` que los recolecte o
  visualice.
- El proyecto Spring Boot en `src/` (raíz) no tiene Dockerfile ni aparece en
  `docker-compose.yml`; su relación con el resto del sistema no está
  documentada.
- No se encontró en el repositorio la imagen `UteqLogo.png` que
  `docs/entrega3/PFC3.tex` intenta cargar como marca de agua de la portada
  (protegido con `\IfFileExists`, por lo que la compilación no falla, pero la
  marca de agua no aparece).
- La rama `feature/entrega-3` contiene además directorios generados por build
  (`pedidos-service/target/`) sin seguimiento en Git; deben limpiarse o
  agregarse correctamente al `.gitignore` una vez corregida su codificación.

### TODO dejados en este README

- `<!-- TODO: verificar -->` sobre el rol específico de cada integrante del
  equipo (sección Equipo): no se encontró esa asignación en el repositorio.
- `<!-- TODO: verificar -->` sobre la versión mínima de Docker requerida
  (sección Requisitos previos): no está documentada en el repositorio.
- `<!-- TODO: verificar -->` sobre la existencia confirmada de endpoints
  `/actuator/health` en cada microservicio del stack por defecto (sección
  Arranque rápido): se infiere de la dependencia `spring-boot-starter-actuator`
  declarada en los `pom.xml`, pero no se ejecutó `docker compose up` para
  confirmarlo en esta generación del README.
- `<!-- TODO: verificar -->` sobre si `src/` (raíz) es legado intencional o
  código a eliminar (sección Arquitectura / Estructura del repositorio).
