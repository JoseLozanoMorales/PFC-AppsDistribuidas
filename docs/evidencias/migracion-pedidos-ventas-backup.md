# Migración piloto de Pedidos, Ventas, Productos e Inventario

- Fecha: 2026-08-07
- Origen: `TiendaTechV19_Completo.backup` (PostgreSQL 18.1)
- Destino aislado: `tiendatech_migracion`
- Clúster: CockroachDB 23.2.4, tres nodos

## Datos migrados

| Entidad | Filas |
|---|---:|
| Usuarios mínimos | 24 |
| Tipos de método de pago | 2 |
| Métodos de pago | 8 |
| Carritos | 5 |
| Detalles de carrito | 3 |
| Órdenes | 16 |
| Detalles de orden | 235 |
| Facturas | 4 |
| Detalles de factura | 4 |
| Categorías | 9 |
| Marcas | 52 |
| Gamas | 3 |
| Tarifas IVA | 3 |
| Productos | 40 |
| Elementos de galería | 322 |
| Registros de inventario | 40 |
| Tipos de movimiento | 3 |
| Subtipos de movimiento | 5 |
| Movimientos | 37 |
| Registros de kardex | 6 |

Los carritos se tomaron excepcionalmente de `public`: la copia de esa entidad
en `pedidos` contiene cinco filas que referencian usuarios inexistentes. Las
cinco filas de `public` y sus tres detalles sí mantienen integridad referencial.

## Validación funcional

- `pedidos-crdb-service` conectado a `tiendatech_migracion`: salud `UP`.
- `ventas-crdb-service` conectado a `tiendatech_migracion`: salud `UP`.
- `GET /api/ordenes`: 16 órdenes.
- `GET /api/facturas`: 4 facturas.
- `GET /api/metodopago/tipos`: 2 tipos.
- Órdenes con usuario inexistente: 0.
- Carritos con usuario inexistente: 0.

## Distribución y tolerancia a fallos

`SHOW ZONE CONFIGURATION FOR RANGE default` confirmó `num_replicas = 3`.
Los seis rangos físicos de `pedidos.orden` mostraron réplicas `{1,2,3}` y
concesiones repartidas entre `z1`, `z2` y `z3`.

Con `crdb-3` detenido:

- Pedidos devolvió las 16 órdenes.
- Ventas devolvió las 4 facturas.
- Métodos de pago devolvió los 2 tipos.

Después de reincorporar `crdb-3`, los tres nodos volvieron a reportar
`is_available=true` e `is_live=true`.

## Productos

La herencia de tablas PostgreSQL se sustituyó por una tabla canónica
`productos.producto` y un campo `atributos JSONB` para las propiedades variables
de los nueve subtipos. El microservicio dejó de llamar objetos de `public`,
funciones y procedimientos almacenados.

Validaciones realizadas:

- salud de Productos: `UP`;
- listado: 40 productos (39 habilitados);
- categorías: 9;
- detalle del producto 1: 15 elementos de galería;
- contenido binario: HTTP 200, 112.767 bytes;
- creación, actualización de stock y desactivación por SQL portable;
- eliminación física posterior de la fila creada para la prueba;
- lectura de detalle y galería disponible con `crdb-2` detenido;
- los tres nodos vivos después de reincorporar `crdb-2`.

## Inventario

El servicio dejó de depender de cuatro funciones y un procedimiento PL/pgSQL.
Las lecturas y el registro de movimientos se implementaron como SQL portable y
una transacción serializable que actualiza Producto, el resumen de Inventario,
el movimiento y el kardex.

Validaciones realizadas:

- salud del servicio: `UP`;
- 40 registros de inventario, 37 movimientos y 6 kardex migrados;
- consulta de stock del producto 1: 299;
- catálogo completo de 5 subtipos;
- entrada y salida compensatoria: stock inicial 299, stock final 299;
- eliminación de los dos movimientos y kardex de prueba, recuperando los
  conteos originales;
- con `crdb-3` detenido se conservaron el stock 299 y los 37 movimientos;
- los tres nodos vivos después de reincorporar `crdb-3`.

## Órdenes a proveedores

El tipo enumerado y las rutinas PostgreSQL fueron reemplazados por columnas
`STRING` con restricciones y operaciones SQL transaccionales. Se conservaron
los cinco estados del flujo original.

Validaciones: 1 proveedor real importado; 0 órdenes y 0 detalles en el backup;
salud `UP`; flujo crear, enviar y recibir en estado final `RECIBIDA`; subtotal
12,00, IVA 1,80 y total 13,80; stock integrado 299 a 300; compensación y
limpieza a 0 órdenes, stock 299 y costo 250. Con `crdb-2` detenido, el servicio
continuó `UP` y devolvió el proveedor; el nodo fue reincorporado después.

## Usuarios

Se migraron 24 usuarios, 3 roles, 4 provincias, 15 ciudades, 7 direcciones y
57 sesiones de auditoría, conservando los identificadores referenciados por
Pedidos, Ventas y Órdenes a proveedores.

El servicio quedó sin llamadas a procedimientos o funciones PostgreSQL.
Lectura de perfil, catálogos, búsqueda, registro con BCrypt y login fueron
validados. El usuario temporal se eliminó después de la prueba. Con `crdb-3`
detenido, el servicio continuó `UP` y leyó correctamente el usuario existente;
el nodo fue reincorporado y los tres terminaron disponibles.

## Alcance

Esta prueba valida los primeros dominios migrados: Pedidos, Ventas, Productos
e Inventario,
con el subconjunto de Usuarios requerido por sus claves foráneas. La base
principal no se considerará completamente migrada hasta incorporar Órdenes a
proveedores y Usuarios, y retirar la conexión PostgreSQL
mononodo del despliegue normal.
