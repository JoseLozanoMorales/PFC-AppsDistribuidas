# Evidencia de cierre H07 - DTOs en Productos y Pedidos

## Hallazgo original

La revisión técnica ACC señaló que Productos y Pedidos exponían directamente
modelos internos en sus respuestas HTTP, acoplando el contrato público a las
clases usadas por la lógica y la persistencia.

## Corrección verificada

- Productos devuelve `ProductoResumenResponse` desde su catálogo.
- Pedidos convierte `Orden` a `OrdenResponse` en listados, consulta individual y checkout.
- Pedidos convierte `DetalleOrden` a `DetalleOrdenResponse` en el detalle paginado.
- La conversión ocurre al final del controlador, por lo que no altera consultas,
  reglas de autorización, idempotencia ni metadatos de paginación.
- Los nombres y tipos JSON existentes se conservan para no romper a los consumidores.

Las clases `Orden` y `DetalleOrden` permanecen internas al servicio y ya no
forman parte de las firmas públicas de `OrdenController`.

## Prueba automática

`OrdenResponseTest` verifica que ambos modelos se convierten a tipos de respuesta
independientes y que el JSON conserva campos representativos del contrato previo.

La prueba se ejecuta con:

```text
mvn test
```

desde `services/pedidos-service`.

## Criterio de cierre

H07 puede cerrarse cuando las pruebas de Productos y Pedidos pasan y ningún
endpoint de sus controladores devuelve directamente `Orden`, `DetalleOrden` o el
modelo interno de Producto.
