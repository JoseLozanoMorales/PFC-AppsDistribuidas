# Migración de la base principal a CockroachDB

## Objetivo

Reemplazar la instancia PostgreSQL mononodo `TiendaTechV19` por el clúster
CockroachDB de tres nodos, conservando los seis esquemas de dominio y sin usar
las copias históricas de `public` como una segunda fuente de datos.

La migración se hará por perfiles. El perfil normal seguirá disponible hasta
que todos los servicios y sus pruebas funcionen contra CockroachDB.

## Origen del modelo

`public` contiene el modelo original. Los esquemas `usuarios`, `productos`,
`inventario`, `pedidos`, `ventas` y `ordenes_proveedores` se derivaron de ese
modelo como separación lógica temporal.

Reglas para construir el modelo canónico:

1. La tabla del esquema de dominio prevalece cuando existe también en `public`.
2. Un objeto exclusivo de `public` no se elimina hasta confirmar si una
   aplicación activa lo utiliza y asignarle un dominio propietario.
3. No se migran dos copias de la misma entidad.
4. Los procedimientos y triggers se conservan solo si son compatibles; la
   lógica restante se traslada al servicio propietario mediante SQL
   transaccional.

## Dependencias actuales de los microservicios

| Servicio | Esquema principal | Estado para CockroachDB | Bloqueo principal |
|---|---|---|---|
| Pedidos | `pedidos` | Parcialmente adaptado | Métodos de pago aún llaman funciones y procedimientos |
| Ventas | `ventas` | Parcialmente adaptado | El perfil normal todavía usa rutinas PostgreSQL |
| Productos | `productos` | No adaptado | Funciones/procedimientos y cuatro dependencias residuales de `public` |
| Inventario | `inventario` | No adaptado | Toda la API de persistencia depende de rutinas PostgreSQL |
| Órdenes a proveedores | `ordenes_proveedores` | No adaptado | Operaciones implementadas mediante procedimientos y un tipo personalizado |
| Usuarios | `usuarios` | No adaptado | Registro, administración, direcciones y auditoría dependen de rutinas PostgreSQL |

Dependencias residuales activas de Productos sobre el esquema histórico:

- `public.f_productos_recientes_con_imagen_menu`
- `public.fn_galeria_v2_listar`
- `public.fn_listar_categorias`
- `public.galeria_productos_v2`

Estas dependencias deben moverse a `productos` antes de retirar `public`.

## Orden de migración

1. Completar `pedidos` y `ventas`, aprovechando los perfiles CockroachDB ya
   existentes.
2. Migrar `productos`, eliminando primero sus dependencias de `public`.
3. Migrar `inventario` y comprobar el flujo de reducción de existencias.
4. Migrar `ordenes_proveedores` y su integración con Inventario.
5. Migrar `usuarios`, autenticación, direcciones y auditoría.
6. Clasificar las funcionalidades exclusivas de `public` (encuestas,
   sugerencias, reseñas y auditorías heredadas) y asignarlas a un dominio o
   declararlas fuera del despliegue activo.

## Avance

### Pedidos y Ventas

- El esquema CockroachDB ya incluye tipos y métodos de pago.
- La factura conserva la instantánea de identificación, contacto y dirección
  que existe en la base principal.
- `MetodoPagoService` dejó de depender de funciones y procedimientos PL/pgSQL;
  ahora usa SQL transaccional compatible con PostgreSQL y CockroachDB.
- El repositorio CockroachDB de Ventas lee la instantánea almacenada en la
  factura, en lugar de reconstruir datos históricos desde el usuario actual.
- `docs/db/exportar-backup-pedidos-ventas.ps1` transforma el backup PostgreSQL
  en una carga ordenada para las tablas canónicas y unifica las particiones
  físicas `orden_historica`/`orden_reciente`.
- La copia de carritos bajo `pedidos` no se usa como fuente: cinco de sus seis
  filas referencian usuarios inexistentes. Los cinco carritos y tres detalles
  originales de `public` sí conservan integridad referencial y son la fuente
  canónica excepcional para esa entidad.
- Pedidos y Ventas compilan correctamente. La prueba Testcontainers permanece
  pendiente mientras Docker no esté iniciado.

### Productos

- Migrados 40 productos, 9 categorías, 52 marcas, 3 gamas, 3 tarifas IVA y
  322 elementos binarios de galería.
- La herencia PostgreSQL de subtipos se reemplazó por `atributos JSONB` en la
  tabla canónica de producto.
- El microservicio ya no depende de `public`, funciones ni procedimientos.
- Lecturas, búsqueda, detalle, galería y CRUD básico funcionan contra
  CockroachDB.
- Pedidos consulta ahora `tiendatech-productos` en el perfil distribuido.
- Productos permaneció disponible durante la caída controlada de un nodo.

### Inventario

- Migrados 40 registros de inventario, 37 movimientos, 6 kardex, 3 tipos y
  5 subtipos.
- El servicio ya no depende de funciones ni procedimientos almacenados.
- El registro de movimientos actualiza Producto, el resumen de Inventario y
  el kardex dentro de una transacción serializable.
- La prueba compensatoria de entrada y salida devolvió el stock al valor
  original y sus registros de auditoría temporales fueron eliminados.
- Ventas consulta ahora `tiendatech-inventario` en el perfil distribuido.
- Inventario permaneció disponible durante la caída controlada de un nodo.

### Órdenes a proveedores

- Migrado el proveedor existente; el backup contenía 0 órdenes y 0 detalles.
- El enum y las rutinas PL/pgSQL se reemplazaron por restricciones y SQL
  transaccional portable.
- Crear, enviar y recibir una orden funciona contra CockroachDB.
- La recepción incrementó el stock en Inventario; la prueba fue compensada y
  retirada sin alterar los datos originales.
- El servicio permaneció disponible durante la caída controlada de un nodo.

### Usuarios

- Migrados 24 usuarios, 3 roles, 4 provincias, 15 ciudades, 7 direcciones y
  57 sesiones históricas.
- Registro, actualización, búsqueda, direcciones, ubicaciones, contraseñas y
  auditoría usan SQL portable en lugar de rutinas PL/pgSQL.
- El registro reversible generó un hash BCrypt válido y el login fue exitoso.
- Usuarios permaneció disponible durante la caída controlada de un nodo.

### Frontend distribuido

- `tiendatech-gateway` publica las interfaces en `http://localhost:8180`.
- Sus seis rutas apuntan exclusivamente a los servicios `*-crdb-service`.
- Se validaron la página principal y los seis dominios con respuestas HTTP 200.
- El contenedor `frontend` tradicional se conserva separado y sin cambios.

## Criterio de finalización

La base principal se considerará distribuida solamente cuando:

- los seis microservicios usen el perfil CockroachDB;
- ninguna conexión de producción apunte a `host.docker.internal:5432`;
- las pruebas funcionales principales sean satisfactorias;
- los rangos tengan `num_replicas = 3` y réplicas en los tres nodos;
- el sistema continúe con un nodo detenido y rechace escrituras sin quórum;
- PostgreSQL pueda apagarse sin afectar al sistema.
