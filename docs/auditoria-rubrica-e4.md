# Auditoría de cumplimiento — Rúbrica Entrega 4 (PFC · ISR-701)

Comparación entre el estado real del repositorio `TiendaTech` (equipo AGLS TiendaTech) y la Guía Integral de la Entrega 4, al 23 de agosto de 2026. Basado en lectura directa del código, no en lo que dicen los README.

## Hallazgo urgente antes de todo lo demás

Durante esta revisión encontré que **`AdminView.vue` ya no existe en el repositorio**. Alguien migró la SPA de Vue a React: `package.json` ahora declara `react`, `react-dom` y `react-router-dom`, `main.tsx`/`App.tsx` usan React Router, y existe `AdminView.tsx` que reimplementa proveedores/órdenes desde cero.

Esa reimplementación es **más atrasada** que lo que construimos juntos hoy en la versión Vue:
- Sigue usando `confirm()` nativo del navegador (el modal propio que armamos no se portó).
- "Recibir orden" usa `prompt()` de JavaScript (peor que el formulario dedicado que teníamos).
- El selector de proveedor/producto en "Crear orden" sigue siendo un `<select>` plano — no tiene el modal con paginación que pediste.
- Sí conserva la validación de RUC/teléfono y la distinción activo/inactivo (eso sí quedó).

Como el `.vue` ya no existe, todo el trabajo de hoy sobre esos tres puntos no está en la app que realmente se sirve. Recomiendo portar esas tres mejoras a `AdminView.tsx` — puedo hacerlo si me confirmas que siga trabajando ahí en React de ahora en adelante.

Nota positiva: React 18 sí es una opción válida según la rúbrica (D2.1), así que este cambio de stack —aunque nos tomó por sorpresa— acerca al equipo al cumplimiento en vez de alejarlo, siempre que se documente con un ADR justificando la elección (la rúbrica lo exige: "el equipo debe justificar su elección con al menos dos criterios cuantitativos").

## Resumen por dimensión

| Dim. | Criterio | Estado | Evidencia |
|---|---|---|---|
| D1 | 1.1 Refactor en capas y SOLID | **No cumple** | `ordenes-proveedores-service`, `ventas-service` y el resto de microservicios usan `controller/service/repository/model/dto`, no los cuatro paquetes exigidos (`presentation/application/domain/infrastructure`). No hay puertos (interfaces) en `domain` ni adaptadores separados en `infrastructure`. |
| D1 | 1.2 Patrones GoF con ADR | **No cumple** | No existe `docs/adr/ADR-005-patrones-gof.md`. El `ADR-005` que sí existe es sobre el gateway JWT, otro tema. Ningún patrón GoF está documentado con nombre/propósito. |
| D2 | 2.1 Web funcional | **Parcial** | La SPA cumple ≥5 rutas (tiene ~12), JWT y guard de admin funcionan. Faltan: internacionalización (no hay `react-i18next` ni cambio de idioma), y el JWT se guarda también en `localStorage` plano (`session.ts`), lo que la rúbrica prohíbe explícitamente ("nunca en localStorage plano"). |
| D2 | 2.2 Web calidad | **No cumple / sin evidencia** | No hay `eslint`/`prettier` configurado en `Apps/web/frontend/webapp` (no aparecen en `package.json`, no hay `.eslintrc`). No hay pruebas (`Vitest`/`Jest`) ni medición de cobertura. Sí hay `Dockerfile` multi-stage. |
| D3 | 3.1 App móvil funcional | **Cumple en buena parte** | `Apps/mobile` es un proyecto Android real (Kotlin + Jetpack Compose) con auth, catálogo, carrito, órdenes, cuenta/checkout, notificaciones y **scanner de cámara** (dos capacidades del dispositivo: cámara y notificaciones — cumple el mínimo de dos). Arquitectura por *feature* con `data/domain/ui`, Room para caché offline. |
| D3 | 3.2 App móvil calidad | **Parcial** | Hay pruebas instrumentadas (`androidTest`: `TiendaTechDatabaseTest`, `NotificationIntegrationTest`) — cumple "al menos una prueba instrumentada". No confirmé pruebas unitarias de ViewModels con JUnit5+coroutines-test, ni que el pipeline publique el APK como artefacto (el `ci.yml` actual sí sube APK debug, ver D5.2). |
| D4 | 4.1 Contratos Pact web+móvil↔backend | **No cumple** | No existe carpeta `tests/contract` ni configuración de Pact en el repo. |
| D4 | 4.2 Persistencia distribuida operando | **Cumple** | El clúster CockroachDB de la E3 sigue funcionando (lo confirmamos hoy mismo con consultas reales); healthchecks presentes en la mayoría de servicios (con la excepción ya reportada antes: `ventas-crdb-service` y `ordenes-proveedores-crdb-service` sin `healthcheck:` en el `docker-compose.yml` actual). |
| D5 | 5.1 Pirámide de pruebas | **No cumple** | No hay pruebas unitarias en `ordenes-proveedores-service` ni `ventas-service` (`src/test` no existe en ninguno de los dos). No hay `tests/integration`, `tests/load` (Locust), ni `tests/e2e-web` (Playwright) en el repo. |
| D5 | 5.2 Pipeline CI/CD end-to-end | **No cumple** | El único workflow es `.github/workflows/ci.yml` con 2 jobs (`android-mobile`, `crdb-tests`). La rúbrica exige `ci-cd.yml` con 7 jobs (`lint, test-backend, test-web, test-mobile, build-images, build-mobile-apk, integration`) que publiquen imágenes en GHCR. Ninguno de los jobs actuales hace build/push de imágenes Docker. |
| D6 | 6.1 Observabilidad completa | **No cumple** | No hay carpeta `ops/` en el repo. No aparece `opentelemetry`, `prometheus` ni `grafana` en `docker-compose.yml`. No hay `trace_id` en logs (Logback actual no está configurado para JSON estructurado, según lo visto en las configuraciones de los servicios). |
| D6 | 6.2 Evidencia operativa (demo) | **No cumple** | Depende directamente de 6.1; sin dashboard no hay demo posible. |
| D7 | 7.1 Evaluación ISO 25010 | **No cumple** | No existe `docs/experimentos/resultados/iso25010.csv` ni protocolo para E4 (`docs/experimentos/protocolo-e4.md` no existe; solo está el de E3). |
| D8 | 8.1 Manuscrito final | **No cumple** | No hay carpeta `entrega4/` ni `.tex` de la E4. Existe `docs/entrega3/PFC3.tex`, pero la rúbrica pide un documento acumulativo nuevo para E4 (≥20 páginas, estructura de la Tabla 3). |
| D8 | 8.3 Trazabilidad E1–E4 | **Cumple en parte** | Las entregas 1–3 sí están presentes y con evidencia real (`docs/entrega1/`, `docs/entrega2/`, `docs/entrega3/`, `docs/evidencias/*` con capturas, CSVs y hasta un video de tolerancia a fallos). Buena base para la sección "Trazabilidad" del manuscrito de E4. |
| D8 | 8.4 Repositorio y README | **Parcial** | Hay `.env.example`, pero como reporté antes contiene secretos reales (AWS, Gmail, JWT) en vez de placeholders — esto es exactamente lo opuesto a "semillas deterministas" y es un riesgo de seguridad real si el repo es público o se comparte. |
| D9 | 9.2 Reflexión ética | **No cumple / sin evidencia** | No encontré ningún documento de reflexión ética ni referencia al ACM Code of Ethics en el repo. |
| D9 | 9.3 Amenazas a la validez | **No cumple / sin evidencia** | No hay sección de amenazas a la validez en ningún documento actual. |
| D9 | 9.4 Reproducibilidad | **Parcial** | `docker compose up` sí levanta todo el sistema (lo hemos probado extensamente esta sesión), pero depende de un clúster CockroachDB externo en AWS con IP que cambia — no es un "desde cero" 100% autocontenido como pide el criterio. |

## Lo que sí está fuerte

Vale la pena decirlo porque no todo es negativo: la arquitectura de microservicios (E2) y la capa de datos distribuida con CockroachDB, fragmentación y consenso Raft (E3) están genuinamente bien trabajadas y documentadas (`docs/adr/ADR-003`, `ADR-004`, `docs/evidencias/`, con pruebas reales de tolerancia a fallos incluyendo video). La app móvil es sustancialmente más completa de lo que esperaría ver a esta altura. Eso es una base sólida — lo que falta es específicamente la "capa E4": calidad, observabilidad, evaluación experimental y el manuscrito.

## Qué puedo hacer yo directamente (dentro de tu alcance: ordenes-proveedores-service, ventas-service, AdminView)

1. Portar a `AdminView.tsx` el modal de confirmación propio, el selector de proveedor/producto con paginación, y las columnas RUC/Contacto/Dirección — para no perder el trabajo de hoy.
2. Refactorizar `ordenes-proveedores-service` y `ventas-service` a los 4 paquetes exigidos (`presentation/application/domain/infrastructure`) y documentar los patrones GoF aplicados en un ADR-005 correcto (el actual ADR-005 tendría que renombrarse o el nuevo usar otro número, hay que coordinarlo con el equipo).
3. Escribir pruebas unitarias para ambos servicios (hoy no tienen ninguna) con dobles de prueba para los repositorios.
4. Corregir que el JWT no se guarde en `localStorage` plano (afecta `session.ts`, compartido — lo tocaría solo si me das luz verde ya que no es "tuyo" en sentido estricto, aunque sí es seguridad).

## Qué requiere coordinación con el equipo (fuera de lo que me pediste tocar)

- Elegir y justificar con ADR el framework web (React ya elegido de facto, falta el ADR).
- Armar el pipeline CI/CD de 7 jobs con GHCR.
- Meter observabilidad (OpenTelemetry + Prometheus + Grafana) — es transversal a todos los servicios.
- Pruebas de carga (Locust), contrato (Pact) y E2E (Playwright/Espresso).
- Evaluación experimental ISO 25010 y el manuscrito LaTeX de la E4.
- Reflexión ética y discusión de amenazas a la validez (secciones de escritura, no de código).
- Rotar los secretos reales que están en `.env.example` (ya lo había flagged antes, sigue sin resolverse).
