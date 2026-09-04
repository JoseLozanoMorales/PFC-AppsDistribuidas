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

## Pendiente / fuera de alcance de este cambio

1. La propagación agregada es del contexto real de OpenTelemetry
   (`traceparent`), no del `X-Trace-Id` de correlación manual de negocio
   (`HttpObservabilityFilter`, Andy) -- ese header sigue sin cruzar el canal
   TCP hacia los logs de `inventario`. No se verificó si esto es necesario
   para el ítem 2 de la rúbrica (correlación en logs) además del ítem 6
   (traza).
2. El servicio `tiendatech-grpc` de reservas (`GrpcReservationEndpoint.java`,
   puerto 9092) existe en el código pero no se usa en el flujo actual
   (`pedidos` habla con `inventario` solo por el puerto TCP 9091); no se
   instrumentó porque no está en la ruta de ejecución real.
3. Los cambios de `docker-compose.yml`/`.env.example` de Grafana (ítem 5) y
   estos dos archivos de instrumentación TCP (ítem 6) siguen sin commitear,
   misma decisión de equipo pendiente que ya se documentó en
   `arranque-limpio-paso15.md` y `paso10-item5-grafana-carga.md`.
