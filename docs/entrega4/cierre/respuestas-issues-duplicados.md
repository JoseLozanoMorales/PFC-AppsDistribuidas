# Respuestas preparadas para incidencias duplicadas

Estado al 4 de septiembre de 2026. Los tres textos fueron publicados en GitHub y los issues se cerraron administrativamente como duplicados. Los límites técnicos indicados siguen vigentes donde corresponda.

## Issue 40

**Acción: reconocer como duplicado atendido por el issue 41**

La observación coincide con el alcance del issue 41. Se implementó `InventarioOutboxProcessor`, que recupera eventos pendientes, reintenta la comunicación con inventario y marca cada evento como procesado únicamente después de recibir confirmación.

**Evidencia:**
- Issue principal: https://github.com/JoseLozanoMorales/TiendaTech/issues/41
- Implementación: https://github.com/JoseLozanoMorales/TiendaTech/commit/2fc3dd1
- Respuesta documentada: https://github.com/JoseLozanoMorales/TiendaTech/issues/41#issuecomment-5499549164

Publicado y cerrado como duplicado del 41: https://github.com/JoseLozanoMorales/TiendaTech/issues/40#issuecomment-5548128749

La idempotencia de extremo a extremo permanece registrada por separado como deuda técnica y no se considera resuelta por este cierre administrativo.

## Issue 46

**Acción: reconocer como duplicado atendido por el issue 47**

La observación coincide con el alcance del issue 47. `ordenes-proveedores-service` incorporó validación JWT, configuración de seguridad, endpoint Actuator y healthcheck.

**Evidencia:**
- Issue principal: https://github.com/JoseLozanoMorales/TiendaTech/issues/47
- Implementación: https://github.com/JoseLozanoMorales/TiendaTech/commit/bb486ff
- Respuesta documentada: https://github.com/JoseLozanoMorales/TiendaTech/issues/47#issuecomment-5499564428

Publicado y cerrado como duplicado del 47: https://github.com/JoseLozanoMorales/TiendaTech/issues/46#issuecomment-5548128977

La respuesta se limita a JWT y salud del servicio y no acredita seguridad integral del sistema.

## Issue 48

**Acción: reconocer como duplicado atendido por el issue 49**

La observación coincide con el alcance del issue 49. El `InventarioClient` de `ordenes-proveedores-service` recibió tiempos máximos explícitos de conexión y lectura.

**Evidencia:**
- Issue principal: https://github.com/JoseLozanoMorales/TiendaTech/issues/49
- Implementación: https://github.com/JoseLozanoMorales/TiendaTech/commit/27c3c70
- Respuesta documentada: https://github.com/JoseLozanoMorales/TiendaTech/issues/49#issuecomment-5499565384

Publicado y cerrado como duplicado del 49: https://github.com/JoseLozanoMorales/TiendaTech/issues/48#issuecomment-5548129164

Los timeouts no sustituyen las políticas de reintento seguro, circuit breaker o idempotencia, que se gestionan en incidencias distintas.

## Comprobación posterior a la publicación — completada

- Cada issue contiene su respuesta y enlace permanente.
- Los issues 40, 46 y 48 están cerrados como duplicados.
- `cierre/issues-corte.json` y la tabla de revisión cruzada reflejan el estado actualizado.
- La deuda conserva únicamente los límites técnicos no resueltos por los issues principales.
