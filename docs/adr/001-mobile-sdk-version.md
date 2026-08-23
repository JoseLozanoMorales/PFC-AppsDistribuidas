# ADR 001: Uso de Android SDK 37 (Baklava) para Compilación y Target

## Estado
Aceptado

## Contexto
El proyecto móvil TiendaTech utiliza las versiones más recientes de las librerías de Jetpack. Durante la configuración inicial de la Fase 0, se detectó que dependencias base del proyecto exigen una versión mínima de compilación superior a la 34 o 36.

### Dependencias Actuales (Fase 0)
Las siguientes librerías ya declaradas en `libs.versions.toml` e implementadas en `app/build.gradle.kts` reportan errores de metadatos AAR si se compila con SDK 36.1 o inferior:
- `androidx.core:core-ktx:1.19.0` (Requiere `minCompileSdk 37`)
- `androidx.core:core:1.19.0` (Dependencia transitiva, requiere `minCompileSdk 37`)

### Dependencias Previstas (Fase 1+)
Se ha verificado que las siguientes librerías que se incorporarán en la Fase 1 también presentan este requisito:
- `androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0` (Requiere `minCompileSdk 37`)
- `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.11.0` (Requiere `minCompileSdk 37`)

El error reportado por el compilador es:
`requires libraries and applications that depend on it to compile against version 37 or later of the Android APIs.`

## Decisión
Se establece `compileSdk = 37` y `targetSdk = 37` en el módulo `app`.

Esta decisión se basa en la compatibilidad obligatoria para poder utilizar las versiones estables actuales de `core-ktx` y las librerías de `lifecycle` que forman el núcleo técnico de la arquitectura Compose elegida.

## Consecuencias
- El entorno de desarrollo debe contar con el SDK 37 instalado.
- Se asegura la compatibilidad con las últimas correcciones de seguridad y rendimiento de las librerías base de Jetpack.
- El `minSdk` se mantiene en 26.
