# Cierre documental de las observaciones

Revisión local del 3 de septiembre de 2026. Este registro complementa, sin sustituir, el corte histórico del 1 de septiembre en `CIERRE-PASO13.md`. Contrasta el apartado del documentalista del plan de cierre (páginas impresas 10–11) con las fuentes actuales. Los estados siguientes describen el trabajo local, no aprobación docente ni publicación en GitHub.

## Correcciones comprobadas

Actualización de sincronización del 4 de septiembre: se incorporó `origin/main` hasta `b690470` y se resolvió el conflicto del LaTeX conservando las correcciones documentales y la nueva evidencia de carga/Grafana y trazas TCP. Los estados fechados el 3 de septiembre que siguen abajo son históricos. Las trazas recibidas corresponden a operaciones separadas de carrito y checkout; no se afirma un único identificador para el recorrido completo. También se recibió `docs/evidencias/arranque-limpio-paso15.md`, cuya aceptación operativa debe contrastarse con sus límites. La nueva campaña de 288 corridas continúa pendiente. La compilación de publicación se comprueba desde una copia que contiene exclusivamente los archivos seleccionados para Git; no ejecuta el sistema.

| Asunto | Estado local y evidencia |
|---|---|
| Denominación TiendaTech | Búsqueda documentada en `../nombre/README.md`, tres capturas históricas y enlace desde el README raíz. Se reconocen coincidencias; no se afirma exclusividad. |
| Registro por entrega, C1 | `registro-cambios.tex`: ocho filas E1–E4, antecedentes, cambios, límites y commits históricos. |
| Trazabilidad, C5 | `trazabilidad-temas.tex` y `cierre/trazabilidad-temas.csv`: 27 entradas con archivo, rango de líneas y commit `96a350b12377b537f979910324ba2fb6c6ee9ba2`. Son referencias a esa revisión, no al futuro commit de entrega. |
| Preguntas de investigación | `PFC4.tex`, etiqueta `sec:preguntas-investigacion`: cinco preguntas; cinco respuestas explícitas en Conclusiones. Se admite falta de evidencia y resultado negativo. |
| Amenazas con mitigación | `PFC4.tex`, sección Amenazas a la validez: cuatro internas, tres externas, además de constructo y conclusión. Se distingue mitigación aplicada de ensayo futuro; no se declara corregido el banco mediante redacción. |
| Bibliografía específica, C9 | `estado-arte-2pc-saga.tex` y `cierre/bibliografia-2pc-saga.json`: cinco fuentes añadidas. Total compilado: veinte fuentes académicas y tres normativas o éticas. El alcance de lectura está declarado, incluido el uso solo del resumen de Daraghmi et al. |
| Duplicados 40, 46 y 48 | La tabla enlaza respectivamente a 41, 47 y 49. Mantiene el estado abierto del corte. Documentar el vínculo no equivale a responder o cerrar el issue remoto. |
| Diferidos y coste de arrastre, C8 | Tabla de deuda en `PFC4.tex`, etiqueta `tab:deuda`: pendientes técnicos, duplicados sin respuesta y observaciones experimentales; separa esfuerzo, arrastre y evidencia de cierre. |
| Resumen, introducción y conclusiones | Se acota el alcance a los datos existentes: laboratorio SQLite serializado, resultados descriptivos y ausencia de validación distribuida o RAG. |
| Residuo `commits.txt` | Ausente del árbol local y marcado como eliminación por Git. Falta incorporar esa eliminación al commit de entrega. |
| Reproducibilidad del documento | PDF de 46 páginas, 23 referencias, compilado con pdfLaTeX y Biber y revisado visualmente. Las nuevas dependencias están indicadas en `README.md`. Esta compilación local no reemplaza la comprobación del futuro commit desde un clon limpio. |

## Evidencia remota recibida el 3 de septiembre

Se actualizó la referencia `origin/main` y se inspeccionaron cinco commits posteriores al corte local, hasta `dbe0d772fe36b9a4a836dc9101eeb0c6cb6c25d9`. No se fusionó código remoto con los cambios locales. La memoria incorpora esta evidencia en `actualizacion-evidencias.tex`, sección 7.4; el PDF actualizado tiene 47 páginas. La fila de compilación anterior describe la versión previa a esta incorporación.

- **Piloto recibido y comprobado:** 240 operaciones, 120 por estrategia. E-2PC termina con stock 319, igual a inicial más movimientos; E-Saga termina con 490 frente a 319 calculado. Se copiaron CSV, bases e informes con sus SHA-256 a `cierre/actualizacion-20260903/`. Ejecutar `python docs/entrega4/cierre/actualizacion-20260903/verificar_piloto.py` desde la raíz reproduce la comprobación sin escribir en las bases.
- **Correcciones de código inspeccionadas:** retiro del candado Python, quinto control del oráculo, calentamiento con carga, doce repeticiones y cálculo exacto con Bonferroni. La campaña de 288 corridas sigue pendiente en el README remoto; los CSV y metadatos históricos del Paso 8 no fueron sustituidos.
- **Instrumentación recibida:** propagación de identificadores, observaciones en memoria, salud del stack, inyector de fallos y ejecutor HTTP. No constituyen todavía una traza exportada, resultados de carga ni demostración de selección real entre protocolos por `COORD`.
- **Alcance de esta integración:** evidencia publicada e inspeccionada, sin ejecutar el sistema ni cerrar issues. Se mantienen como pendientes los paquetes siguientes; para el banco, ya se recibió el piloto local, pero falta la campaña completa y el contraste distribuido.

## Evidencia que debe recibirse para el cierre final

La asignación siguiente orienta la recepción documental según el plan por rol. No representa mensajes enviados ni fechas acordadas con los integrantes.

| Entrega pendiente | Responsable según el plan | Paquete mínimo a recibir | Actualización documental posterior |
|---|---|---|---|
| Banco distribuido y campaña 2PC/Saga | Jeremy (implementación), Andy (ejecución y análisis), Jhinson (consistencia por agregado) | Commit, protocolo, participantes y topología, operaciones/fallos, semillas y orden, datos crudos por repetición, historiales del oráculo, comandos y análisis reproducible. | Diseño, resultados, figuras, discusión, amenazas, preguntas 1–3 y 5, resumen y CAP. Conservar el laboratorio anterior como antecedente identificado. |
| Carga, cobertura y CI | Andy | Alcance y herramienta de cobertura, reportes originales; ejecuciones CI roja y verde identificadas; configuración y resultados de carga con fecha y commit. | Calidad, tablas ISO y conclusiones, con unidades y límites coherentes con lo realmente medido. |
| Observabilidad bajo carga | Jhinson, coordinado con Andy | Traza exportada de una compra, identificador correlacionable y captura del panel de la misma sesión de carga; configuración y fecha. | Observabilidad y deuda C7; comprobar que la traza recorre el flujo afirmado. |
| Arranque desde entorno limpio, P3 | Jhinson y Jeremy | Entorno, commit, comando exacto, registro de arranque y comprobación funcional desde la copia limpia. | Reproducibilidad operativa y estado de P3, sin confundir compilación del PDF con arranque del sistema. |
| Asistente y comparación con RAG | Responsable técnico del asistente y Calidad; confirmar reparto | Conjunto independiente etiquetado, configuración de ambos enfoques, respuestas reales y cálculo reproducible de métricas. | Pregunta 4, resultados de compatibilidad y límites del asistente. |
| Resolución o diferimiento de issues | Responsable de cada issue | Respuesta enlazada, commit y prueba cuando aplique; para diferidos, motivo, coste de arrastre y condición de revisión. | Refrescar inventario y deuda con una nueva fecha de corte; conservar el JSON histórico. |

Cada paquete debe identificar el commit y cualquier modificación local, fecha y zona horaria, entorno, comando, archivos originales y procedimiento de análisis. Si un resultado cambia, actualizar todas sus menciones y conservar la procedencia de la versión anterior. Un archivo entregado sin contexto no basta para sustituir una conclusión.

## Secuencia para terminar

1. Recibir y comprobar los paquetes técnicos; registrar qué requisitos demuestran y cuáles siguen abiertos.
2. Actualizar tablas y figuras desde el análisis, después discusión, amenazas, conclusiones y finalmente resumen y abstract.
3. Revalidar trazabilidad contra el commit que realmente se entregará, sin cambiar el hash histórico manteniendo rangos antiguos.
4. Obtener revisión de las reflexiones y declaraciones por sus autores. La comprobación institucional de similitud sigue sin ejecutarse; no se certifica un porcentaje.
5. Incorporar los archivos del paquete documental a Git y revisar el cambio completo. Hay modificaciones locales ajenas a este cierre que deben seleccionarse por separado.
6. Compilar desde un clon limpio del commit final, revisar el PDF y repetir la comprobación operativa P3 con el equipo. Registrar ambas verificaciones por separado.

El documentalista puede mantener este inventario y preparar la integración mientras llegan las evidencias. El cierre de resultados y la validación del paquete final dependen de esas entregas y revisiones.

## Verificación de publicación del 4 de septiembre

La versión combinada con el remoto se compiló desde una copia limpia de los archivos seleccionados para Git: 52 páginas, sin citas indefinidas ni desbordamientos. Se revisaron visualmente la tabla E1 y la sección de carga y observabilidad. La verificación de hashes y stock del piloto pasó también en esa copia. Se conservaron fuera del commit los cambios locales de `.env.example`, temporales, respaldos y otros trabajos no pertenecientes a este cierre.
