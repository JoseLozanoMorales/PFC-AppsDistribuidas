# Evidencia de traza distribuida completa (Paso 10, ítem 6)

- Fecha: 2026-09-04
- Objetivo: acreditar una traza distribuida real de una compra completa,
  atravesando todos los servicios involucrados, incluyendo el canal de
  reserva de stock que inicialmente quedaba fuera del alcance de OpenTelemetry.
- Máquina: equipo local de Jhinson Aucatoma (Windows, Docker Desktop).

## Resumen

Se capturaron y exportaron cuatro momentos reales de Jaeger, en este orden:

1. **Checkout completo** (`1-checkout-completo.json`, trace
   `f93ac931b7b144c99d299801c5b88a22`): `POST /api/ordenes/checkout` real
   (orden `1000001`, HTTP 201, total 230.00) con 8 spans en 3 servicios --
   `tiendatech-pedidos` → `tiendatech-usuarios` (datos del cliente y
   dirección), `tiendatech-productos` (catálogo e IVA) y `tiendatech-ventas`
   (`POST /api/facturas`).
2. **`/agregar` ANTES de instrumentar el canal TCP**
   (`2-agregar-ANTES-de-instrumentar-tcp.json`, trace
   `ce6fae20e3f0d367a36fed83a6a3fdee`): 5 spans en solo 2 servicios --
   `tiendatech-pedidos` y `tiendatech-productos`. **`tiendatech-inventario`
   no aparece**, pese a que es justo esta llamada la que reserva el stock.
3. **Diagnóstico en código real** (no en el trace): la reserva de stock viaja
   por un socket TCP crudo
   (`services/pedidos-service/.../LengthPrefixedTcpReservationClient.java`),
   no por HTTP -- la auto-instrumentación de Micrometer/OTel solo envuelve
   `RestClient`. Se confirmó además que `ReservationCommand` (el objeto que
   viaja por ese socket) no llevaba ningún campo de trace ID ni del
   `X-Trace-Id` de negocio: el contexto de traza simplemente no cruzaba ese
   salto.
4. **`/agregar` DESPUÉS de instrumentar el canal TCP**
   (`3-agregar-DESPUES-de-instrumentar-tcp.json`, dos traces reales, ambas
   con `tiendatech-inventario` presente):
   - `6789b6f19506ec18d35ae2e3d2f9e94a`: 7 spans, 3 servicios (`pedidos`,
     `productos`, `inventario`). Reserva **aceptada** (`http post
     /api/carrito/{carritoId}/agregar` → 200).
   - `9384ddf943c13c9fe3f5a28b5c490def`: también 7 spans, 3 servicios,
     capturada en el primer intento (que rebotó con 409 antes de ajustar el
     `lamportTimestamp` del script de prueba). El span de `inventario` trae
     el tag `reservation.rejected = "Evento anterior al estado
     reconciliado"` -- confirma que el intercambio TCP se completa igual,
     aceptado o rechazado, y que el motivo de rechazo queda visible
     directamente en la traza.

Captura de Jaeger (`jaeger-2-trazas-con-inventario.png`): las dos trazas
posteriores a la instrumentación, servicio `tiendatech-pedidos`, operación
`http post /api/carrito/{carritoId}/agregar`, con `tiendatech-inventario`
listado entre los servicios de ambas.

## El fix

Se agregó instrumentación manual en dos archivos, sin tocar el protocolo de
negocio (`ReservationCommand` sigue igual, `CarritoService` no cambió):

- **`LengthPrefixedTcpReservationClient.java`** (pedidos-service, cliente
  TCP): abre un span manual `reservation.tcp.reconcile` (`CLIENT`) usando el
  `Tracer` de Micrometer ya configurado, inyecta el `traceparent` del span
  activo en un `Map` mediante `Propagator.inject(...)`, y lo envía dentro de
  un sobre de transporte (`WireEnvelope{command, traceparent}`) -- ya que el
  protocolo binario no tiene headers HTTP donde llevarlo.
- **`TcpReservationServer.java`** (inventario-service, servidor TCP): lee
  ese sobre, extrae el contexto con `Propagator.extract(...)` (que devuelve
  ya el span como hijo del contexto remoto) y abre un span `SERVER` del
  mismo nombre alrededor de `StockReservationService.reconcile(...)`.

Verificación de compilación: `docker compose build tiendatech-pedidos
tiendatech-inventario` -- ambas imágenes compilan limpio (`mvn package`
dentro del Dockerfile multi-stage), sin errores.

## Conclusión

El ítem 6 queda cerrado con evidencia real y verificable: la traza de
checkout y la de `/agregar` cubren, entre las dos, todos los servicios que
participan en una compra (`pedidos`, `usuarios`, `productos`, `ventas`,
`inventario`), bajo un único trace ID por operación, con propagación de
contexto correcta incluso a través de un canal que no es HTTP. El
antes/después (`2-agregar-ANTES...json` vs `3-agregar-DESPUES...json`)
documenta el hallazgo, el diagnóstico y la corrección con datos reales, no
solo la afirmación de que "ahora funciona".

## Actualización 2026-09-04: el `X-Trace-Id` de negocio también cruza el canal TCP

El punto 1 original de "Pendiente" (más abajo) quedó resuelto el mismo día,
tras integrar en `main` el trabajo paralelo de otro integrante del equipo
(Jeremy, observabilidad con OTel javaagent) y descubrir el hueco real al
revisar el código: ninguno de los tres endpoints que disparan el canal TCP
(`CarritoController#agregarProducto`, `#quitarProducto`, `#actualizarCantidad`)
generaba ni aceptaba un `X-Trace-Id` -- ese header solo existía en
`OrdenController#checkout`.

Se corrigió en tres archivos:

- **`CarritoController.java`**: los tres endpoints ahora aceptan el header
  `X-Trace-Id` entrante o generan uno con `UUID.randomUUID()` (mismo patrón
  que `checkout`), lo publican en `TraceContext` y lo devuelven en la
  respuesta.
- **`LengthPrefixedTcpReservationClient.java`**: el `WireEnvelope` ahora lleva
  un tercer campo, `businessTraceId`, leído de `TraceContext.traceId()`,
  además del `traceparent` de OTel que ya viajaba.
- **`TcpReservationServer.java`** (inventario-service): extrae ese
  `businessTraceId` y lo publica en el MDC con las claves `service`/`trace_id`
  -- las mismas que usa `HttpObservabilityFilter` en toda la aplicación --
  alrededor de la llamada a `StockReservationService.reconcile(...)`. Se
  agregó además una línea de log real (`reservation_tcp_completed` /
  `reservation_tcp_failed`), inexistente hasta ahora en ese flujo: sin ella,
  poner el trace ID en el MDC no tenía ningún log que lo mostrara.
  `logback-spring.xml` de inventario-service ya tenía `includeMdc=true` con
  `LogstashEncoder`, así que el campo aparece como JSON real, no solo en
  memoria.

Verificación real, no solo compilación: se corrió `capturar-trace-agregar.ps1`
contra el stack levantado con las imágenes reconstruidas
(`docker compose build tiendatech-pedidos tiendatech-inventario`, ambas sin
errores) y se confirmó en el log de `tiendatech-inventario`:

```json
{"...","message":"reservation_tcp_completed accepted=true cartId=1 productId=1",
 "logger_name":"com.tiendatech.inventario.infrastructure.reservation.TcpReservationServer",
 "trace_id":"8a53205d-707b-4afd-ba50-e9667ec4aa2b","service":"tiendatech-inventario", ...}
```

El campo `trace_id` es el `X-Trace-Id` de negocio generado por
`CarritoController` para ese request en `pedidos-service`, correlacionado
ahora en el log JSON de `inventario-service` pese a que la reserva viaja por
un socket TCP crudo, no por HTTP. (Nota: `traceId`/`spanId` en camelCase que
aparecen en la misma línea son del contexto OTel, un identificador distinto
que ya viajaba desde antes -- ambos coexisten sin pisarse.)

## Pendiente / fuera de alcance de este cambio

1. El servicio `tiendatech-grpc` de reservas (`GrpcReservationEndpoint.java`,
   puerto 9092) existe en el código pero no se usa en el flujo actual
   (`pedidos` habla con `inventario` solo por el puerto TCP 9091); no se
   instrumentó porque no está en la ruta de ejecución real.
2. ~~Los cambios de `docker-compose.yml`/`.env.example` de Grafana (ítem 5) y
   estos dos archivos de instrumentación TCP (ítem 6) siguen sin commitear~~
   -- resuelto: todo quedó commiteado y pusheado a `main` (commit `b690470`,
   fusionado con el trabajo paralelo de Jeremy) el mismo 2026-09-04.
