# ADR 002: aplicación Android nativa para el cliente móvil

- Estado: Aceptado
- Fecha: 21 de agosto de 2026

## Contexto

La entrega requiere una aplicación móvil de compra que reutilice el Gateway y los servicios distribuidos existentes. El repositorio ya incluía un proyecto Android con Kotlin y Jetpack Compose.

## Decisión

Construir una aplicación Android nativa única con Kotlin, Compose y Material 3, compatible desde API 26. Mantener una arquitectura por características dentro de un solo módulo Gradle durante la primera versión.

## Criterios cuantitativos

| Criterio | Android nativo | Multiplataforma nueva |
| --- | ---: | ---: |
| Plataformas exigidas por la entrega | 1 de 1 | 1 de 1 |
| Proyectos base reutilizables en el repositorio | 1 | 0 |
| Integraciones Android directas requeridas (cámara, almacenamiento seguro, notificaciones) | 3 | 3 con capas adicionales |
| Módulos Gradle iniciales | 1 | al menos 2 habituales |
| SDK mínimo exigido | API 26 | no aporta reducción del requisito |

## Consecuencias

- Se reutilizan directamente CameraX, Android Keystore, Room, DataStore y canales de notificación.
- No se obtiene una versión iOS; no forma parte del alcance confirmado.
- Compose permite compartir componentes y estado dentro de Android sin introducir otro framework.
- Si posteriormente se exige iOS, deberá evaluarse una migración o una capa compartida como una decisión independiente.
