# Plan de cierre — Entrega 4 (PFC · ISR-701)

Plan de trabajo priorizado para maximizar la nota de la rúbrica de E4, partiendo
del estado real verificado el 24 de agosto de 2026 (ver `docs/auditoria-rubrica-e4.md`
para el detalle dimensión por dimensión, algo desactualizado ya en D1.1/D1.2).

Objetivo explícito: no se apunta al 100%, se apunta a maximizar puntos por
esfuerzo invertido, en el tiempo que quede hasta la Semana 17. Los pesos entre
paréntesis son los de la Tabla 4 de la rúbrica.

## Fase 0 — Higiene urgente (1 día, protege puntos que ya se ganaron)

Estos ítems no suman puntos nuevos directamente, pero su ausencia puede hacer
perder puntos ya ganados o generar una mala primera impresión en la defensa.

1. **Push completo a GitHub.** El `main` remoto todavía tiene la estructura
   vieja (`frontend/`, `usuarios/`, `src/` en la raíz) y no refleja el refactor
   en capas ni el trabajo de esta sesión. Sin esto, quien evalúe desde el repo
   ve un proyecto mucho más atrasado del que realmente es.
2. **Rotar los secretos reales en `.env.example`.** Debe contener placeholders,
   no credenciales de AWS/Gmail/JWT reales (D8.4, además de ser un riesgo de
   seguridad si el repo se comparte).
3. **Agregar `healthcheck:` a `ventas-crdb-service` y
   `ordenes-proveedores-crdb-service`** en `docker-compose.yml` (D4.2 — los
   demás servicios ya lo tienen, a estos dos les falta; 10 minutos).
4. **Sacar el JWT de `localStorage` plano** en `session.ts` (D2.1 — la rúbrica
   lo prohíbe explícitamente; usar `httpOnly` cookie o `sessionStorage`).

## Fase 1 — Cierres de esfuerzo medio y buen retorno (días 2-4)

5. **Pipeline CI/CD de 7 jobs con GHCR (D5.2, 5%).** Ya existe
   `.github/workflows/ci.yml` con 3 jobs (`android-mobile`, `crdb-tests`,
   `armado-ia-tests`) — no hay que empezar de cero, hay que ampliarlo a
   `lint, test-backend, test-web, test-mobile, build-images, build-mobile-apk,
   integration` y agregar el build/push de imágenes Docker a GHCR (Listado 2
   de la guía es la referencia directa).
6. **Reproducibilidad "desde cero" (D9.4, 2%).** El `docker-compose.override.yml`
   con CockroachDB local que armamos hoy para pruebas ya resuelve el 80% de
   este criterio (el sistema depende hoy de un cluster AWS con IP fija, lo
   cual la rúbrica penaliza). Falta documentarlo como el modo "reproducible"
   oficial en el README y probarlo en una máquina limpia.
7. **Internacionalización de la web (parte de D2.1, 8%).** Agregar
   `react-i18next` con español e inglés como mínimo en las rutas principales.

## Fase 2 — Inversión grande, alto peso combinado (días 5-10)

8. **Observabilidad distribuida (D6.1 + D6.2, 8% combinado).** El bloque más
   grande que falta por completo: `opentelemetry-spring-boot-starter` en el
   microservicio principal, OTel Collector en el compose, reutilizar métricas
   Prometheus de E3 y sumar las 4 nuevas (`http_requests_total`,
   `http_request_duration_seconds`, `app_business_events_total`,
   `app_active_sessions`), logs JSON con `trace_id`, y un dashboard Grafana
   con los 6 paneles obligatorios exportado como `ops/grafana/pfc-dashboard.json`.
   Es mecánico pero extenso — buen candidato para que lo lleve una persona del
   equipo en paralelo a todo lo demás.
9. **Pirámide de pruebas (D5.1, 6%).** Pruebas unitarias en
   `ordenes-proveedores-service` y `ventas-service` (hoy no tienen ninguna),
   `tests/integration` con Testcontainers, `tests/e2e-web` con Playwright,
   `tests/load` con Locust (2 escenarios mínimo). Priorizar cobertura en la
   lógica de negocio nueva (recepciones, costeo, CHECK de precio/costo) antes
   que perseguir el 70% en toda la superficie del código — es más defendible
   en la pregunta obligatoria 5 del Anexo B.
10. **Contratos Pact (D4.1, 5%).** El de mayor riesgo de quedar "a medias":
    si el tiempo aprieta, prioriza dejar el contrato **web↔backend** en verde
    (cliente principal) antes que intentar los dos consumidores a la vez —
    parcial documentado vale más que nada.

## Fase 3 — Evaluación experimental y escritura (días 11-14)

11. **Evaluación ISO 25010 (D7.1, 5%).** Depende de que Fase 2 (Locust +
    Grafana) ya esté funcionando: se necesitan las métricas reales para medir
    fiabilidad, eficiencia, seguridad, mantenibilidad y compatibilidad con
    IC 95%.
12. **Reflexión ética y amenazas a la validez (D9.2 + D9.3, 4%).** Puro texto,
    bajo esfuerzo — se puede escribir en paralelo desde ya, no depende de nada
    técnico. Empieza esto en paralelo a la Fase 1, no lo dejes para el final.
13. **Manuscrito LaTeX (D8.1 + D8.2 + D8.3, 11% combinado).** Empieza el
    esqueleto ahora mismo con lo que ya está firme y no cambia: arquitectura
    heredada de E1-E3, C4, ADRs, manual de la app móvil (ya está sustancialmente
    completa). Deja huecos marcados para las secciones que dependen de las
    Fases 1-2 (observabilidad, pruebas, ISO 25010) y ciérralas al final.

## Fase 4 — Ensayo de defensa (últimos 2-3 días)

14. **Defensa oral (D9.1, 5%).** Ensayar las 8 preguntas obligatorias del
    Anexo B con todo el equipo, sin leer. Depende de que la demo funcione en
    vivo — no lo dejes para la noche anterior.

## Resumen de prioridad si el tiempo se acorta

Si hay que sacrificar algo, en este orden es donde menos duele:

1. Cobertura de pruebas al 70% exacto en todas las capas (quédate con
   cobertura fuerte solo en lo crítico).
2. Contrato Pact del consumidor móvil (deja al menos el de web en verde).
3. Los 6 paneles completos del dashboard (4-5 bien hechos con datos reales
   pesan más que 6 improvisados).

Lo que **no** se debe sacrificar bajo ninguna circunstancia: la aplicación web
y la aplicación móvil ambas funcionando (factor ×0,5 si falta una), y el
manuscrito con la estructura mínima de la Tabla 3 aunque algunas secciones
queden con datos parciales pero honestos.
