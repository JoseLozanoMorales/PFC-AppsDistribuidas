# Ejecución conjunta del punto 3

- Inicio de la sesión: 2026-09-04 08:44:12 America/Guayaquil.
- Entorno congelado: `environment.txt`.
- Carga oficial: 50 usuarios, tasa de arranque 5 usuarios/s y duración de 60 s.
- Resultado de la repetición estable: 2112 solicitudes, 0 fallos, 36.45 solicitudes/s y P95 agregado de 610 ms.
- Fiabilidad: 0 respuestas 5xx de 2112 (0 %), por lo que cumple el umbral menor a 1 %.
- Rendimiento: P95 de 610 ms, por lo que no cumple el umbral estricto menor a 500 ms.
- Dashboard: `dashboard.png`, capturado desde Grafana con la ventana de la sesión completa.
- Trazado no destructivo: `trace/gateway-read-traces.json`, exportación de Jaeger con 20 trazas del Gateway.
- Disponibilidad: en ejecución durante 3600 s continuos; el resultado se incorporará al terminar el recolector oficial.
- Compra real: orden creada correctamente con HTTP 201 usando una dirección y
  un método de pago ficticios, ambos identificados con el ID 740. La respuesta
  está en `trace/checkout-success.json`.
- Traza distribuida de la compra: `trace/purchase-traces.json`, trace ID
  `68e1fc99833f6287209861012769564b`. Incluye Gateway, Pedidos y Ventas, la
  operación `POST /api/ordenes/checkout`, persistencia de la orden y su detalle,
  eliminación del carrito y creación de encabezado, cuerpo y outbox de factura.

## Incidencia y repetición

La primera carga (`load/`) no se usa como resultado final: produjo 1469 fallos de
1621 solicitudes debido al límite de 300 solicitudes/minuto del Gateway y a una
consulta correlacionada de galería que agotaba el presupuesto de memoria de
CockroachDB. Se sustituyó esa consulta por una agregación `MIN(galeria_id)` y se
repitió la carga con un límite operativo suficiente para el escenario oficial.
La evidencia válida de desempeño y fiabilidad está en `load-final/`.
