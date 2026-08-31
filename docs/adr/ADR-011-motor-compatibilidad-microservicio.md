# ADR-011: Motor de compatibilidad como microservicio independiente

- Estado: aceptada en la Entrega 1 (27 de mayo de 2026); formalizada como ADR independiente y ratificada — con una corrección de alcance — en la Entrega 4 (agosto de 2026). Existía únicamente como prosa dentro de `docs/entrega1/entrega1.pdf` (ADR-03).
- Fecha de origen: 2026-05-27
- Fecha de esta formalización: 2026-08-31
- Participación: decisión revisable conjuntamente por los cuatro integrantes

## Contexto

La validación entre procesador, placa madre, RAM, GPU, almacenamiento y fuente de poder requiere reglas técnicas que pueden cambiar con frecuencia. Mezclar esta lógica con el catálogo o con el frontend arriesga acoplar dos ritmos de cambio distintos.

## Decisión

Se implementa el motor de compatibilidad de hardware como un microservicio especializado, separado del catálogo.

## Alternativas consideradas

1. Incluir la lógica de compatibilidad dentro del catálogo: descartada por mezclar responsabilidades (el catálogo es un CRUD de productos; el motor de compatibilidad es un evaluador de reglas técnicas con estado propio).
2. Validar la compatibilidad en el frontend: descartada por riesgos de manipulación (un cliente podría alterar la validación en el navegador) y baja confiabilidad.

## Consecuencias (previstas en E1)

- Mejora la precisión y mantenibilidad del sistema.
- Requiere comunicación constante con el catálogo de productos para mantener actualizada la información técnica.

## Consecuencias observadas en la implementación (actualización E4) — corrección de alcance

Esta es la decisión de E1 que más se corrigió al construir el sistema, y vale la pena decirlo con la misma honestidad que exige el resto de esta entrega en vez de sostener la formulación original:

- E1 describía el motor de compatibilidad como basado en "modelos lógicos de programación por restricciones" (*constraint programming*) con "co-clustering explicable". Lo que efectivamente se construyó, `armado-ia`, es un servicio Python/FastAPI que combina un motor de reglas determinista con un cliente a un modelo de lenguaje grande (AWS Bedrock) para redactar explicaciones, con una estrategia determinista de respaldo cuando el LLM no está disponible (`DeterministicExplicacionClient`, ver Sección 4.3). No se implementó un *solver* de restricciones combinatorio ni co-clustering: la validación de compatibilidad real es más simple (reglas explícitas) que lo que E1 planteó.
- Este cambio no invalida la decisión de aislarlo como microservicio propio — esa parte de E1 sigue siendo correcta y se sostiene con evidencia: `armado-ia` efectivamente vive en su propio contenedor, con su propio pipeline de pruebas (`pytest-cov`, gate de cobertura ≥70 %) y su propio ciclo de CI independiente de los seis servicios Java (Sección 7.3). Lo que se corrige es la descripción técnica de *cómo* razona el motor por dentro, no la justificación arquitectónica de separarlo.
- La razón del cambio no fue un descubrimiento de que la propuesta original fuera inviable, sino una decisión pragmática de alcance bajo el tiempo disponible del equipo: un *solver* de restricciones formal exigía una inversión de diseño que compitió directamente con asegurar primero la persistencia distribuida (Sección 4.4) y la calidad de las aplicaciones cliente (Secciones 5 y 6). Se documenta aquí en vez de en silencio, siguiendo el mismo criterio de priorización explicado para los contratos Pact (Sección 7.2).
