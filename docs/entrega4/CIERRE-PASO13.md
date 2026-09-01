# Verificación de cierre — Paso 13

Corte: 1 de septiembre de 2026. Fuente: `PFC4.tex`. El cierre documental no certifica que todos los pasos técnicos estén aprobados.

## Revisión de los 18 componentes

| Componente exigido | Evidencia / estado |
|---|---|
| Portada e índices automáticos | Incluidos: contenido, figuras y tablas. |
| Resumen bilingüe, máximo 250 palabras | Incluidos; páginas completas con 199 y 170 palabras respectivamente, contando palabras clave y rótulos. |
| Introducción cuantificada | Tamaños del conjunto analítico y banco, objetivos, alcance y estructura; no se inventó impacto comercial. |
| Estado del arte, 15 fuentes | Quince fuentes académicas con DOI contrastado mediante metadatos, agrupadas con síntesis crítica. |
| Diseño C4 1–3, despliegue y ADR | Cuatro vistas propias esquemáticas, fronteras y ADR enlazados; la vista de contenedores agrupa los seis servicios Java por legibilidad. |
| Propiedades distribuidas | Consistencia por agregado, fragmentación, réplica, fallos y reloj lógico, con límites. |
| Implementación | Comunicación, datos, procesamiento y capas; dos fragmentos en `lstlisting`, sin capturas de código. |
| Diseño del estudio | Factores, repeticiones, invariantes, métricas y análisis; diferencias con el protocolo requerido declaradas. |
| Resultados | 24 condiciones, datos de Spark y clúster, tablas y gráficas propias. No son experimentos nuevos. |
| Discusión | Separada; no extrapola SQLite ni el banco de reglas a producción/RAG. |
| Amenazas | Cuatro categorías con mitigaciones; cuatro internas y tres externas. |
| Calidad | ISO 25010:2023/25023:2016, cobertura acotada, complejidad, CI, carga y observabilidad. Se distinguen controles de mediciones pendientes. |
| Revisión cruzada | 63 issues: acción, estado y enlace; 50 cerrados y 13 abiertos (10 pendientes técnicos y 3 duplicados aparentes). No se cambiaron sus estados. |
| Conclusiones | Cinco respuestas explícitas a preguntas derivadas del banco; respuestas negativas donde falta evidencia. |
| Conclusiones individuales | Textos actualizados: 274, 273, 259 y 258 palabras; requieren lectura y aprobación personal. |
| Ética | Más de media página, con referencia ACM/IEEE-CS y aplicación al proyecto. |
| Autoría e IA | Roles y herramientas comunicados por el equipo; sin firmas, porcentajes ni aprobación inventada. |
| Bibliografía IEEE/Biber | 18 entradas citadas: 15 académicas con DOI y 3 institucionales con URL. |

## Controles formales

- PDF de 39 páginas tras la migración: 7 preliminares, 30 de cuerpo y 2 de bibliografía. El cuerpo supera las 20 páginas excluyendo portada, índices, resúmenes y bibliografía.
- Compilación pdfLaTeX → Biber → pdfLaTeX → pdfLaTeX satisfactoria con TeX Live 2026.
- Sin referencias/citas sin resolver ni cajas de texto desbordadas. Solo sustitución tipográfica no fatal de negrita versalita de Latin Modern.
- Ocho figuras con pie, etiqueta y referencia; trece tablas con `booktabs`, etiqueta y referencia. Cuatro ecuaciones numeradas y citadas.
- Imágenes relativas; C4 propio en TikZ; figuras estadísticas regenerables desde los CSV conservados.
- Revisión visual realizada sobre páginas rasterizadas; bloques de código no divididos entre páginas.
- README de la entrega contiene secuencia exacta y alternativa Docker. README principal enlaza la memoria actual y advierte sobre sus tablas históricas.
- El contenido de cierre se migró a `PFC4.tex` y a la bibliografía compartida original; se preservaron las referencias históricas adicionales. No hubo commit, push ni cambios de estado en GitHub.

## Lo que no puede certificarse solo con documentación

1. Aprobación personal de conclusiones y declaración de contribuciones por los cuatro integrantes.
2. Similitud inferior al 15 %: falta el informe de la herramienta institucional.
3. Lectura literal de “todas las referencias con DOI”: las dos normas y el código ético requieren URL institucional; no se inventaron identificadores. Hay quince fuentes académicas con DOI.
4. Cumplimiento experimental íntegro: falta coordinación entre participantes independientes, condiciones temporales completas y comparación real reglas/RAG. El banco local tiene bloqueo global, semillas distintas y oráculo limitado.
5. Disponibilidad de una hora, p95 y tasa de errores oficiales del despliegue; la prueba de carga anterior no se convirtió en una aprobación.
6. Resolución técnica de los diez pendientes y consolidación administrativa de los tres duplicados.

Se puede cerrar la memoria con estos resultados y límites explícitos; no corresponde convertir estas reservas en afirmaciones de cumplimiento completo.
