-- Idempotencia de POST /api/ordenes/checkout (pedidos-service)
-- =============================================================
--
-- ESTADO: el codigo (com.example.pedidos.idempotencia.*, OrdenService,
-- OrdenPersistenceService, OrdenController) ya implementa el soporte de
-- Idempotency-Key completo, pero esta APAGADO por defecto
-- (pedidos.idempotencia.enabled=false) porque la tabla de abajo NO existe en
-- docs/db/schema.sql (archivo compartido del equipo, fuera de esta carpeta).
--
-- QUE FALTA PARA ACTIVARLO:
--   1) Que el equipo integre el CREATE TABLE de abajo en docs/db/schema.sql
--      (mismo lugar/estilo que inventario.solicitud_idempotente).
--   2) Aplicar el schema actualizado contra la base (crdb-init o el flujo que
--      use el equipo para eso).
--   3) Levantar pedidos-service/pedidos-crdb-service con
--      PEDIDOS_IDEMPOTENCIA_ENABLED=true (o pedidos.idempotencia.enabled=true
--      en application.properties). Con eso JdbcIdempotenciaRepository se
--      registra como bean (esta con @ConditionalOnProperty) y el checkout
--      empieza a usar la tabla.
-- Sin estos 3 pasos, mandar el header Idempotency-Key no rompe nada: el
-- servicio loguea un WARN y checkout se comporta exactamente igual que antes.


-- -------------------------------------------------------------------------
-- DDL propuesto (integrar en docs/db/schema.sql junto al resto de
-- pedidos.*). Sigue el patron de inventario.solicitud_idempotente
-- (clave/payload_hash/creado_en varchar+varchar+timestamptz), mas lo minimo
-- funcional que necesita este caso: alcance por usuario y referencia a la
-- orden creada.
-- -------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pedidos.solicitud_idempotente (
    usuario_id    INT8        NOT NULL,
    clave         VARCHAR     NOT NULL,
    payload_hash  VARCHAR     NOT NULL,
    orden_id      INT8        NOT NULL,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_solicitud_idempotente PRIMARY KEY (usuario_id, clave),
    CONSTRAINT fk_solicitud_idempotente_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios.usuario (usuario_id),
    CONSTRAINT fk_solicitud_idempotente_orden
        FOREIGN KEY (orden_id) REFERENCES pedidos.orden (orden_id)
);

-- Por que PRIMARY KEY (usuario_id, clave) y no un chequeo previo en codigo:
-- la PK es la garantia atomica. Si dos peticiones concurrentes llegan con la
-- misma Idempotency-Key, ambas intentan un INSERT con el mismo par
-- (usuario_id, clave) DENTRO de la misma transaccion que crea la orden
-- (ver OrdenPersistenceService.crearOrdenDesdeCarrito). La base garantiza que
-- solo una gana; la otra transaccion completa (orden + detalle_orden
-- incluidos) hace rollback, y el codigo relee la fila ganadora para devolver
-- esa orden. Un "SELECT primero, INSERT despues" sin la PK como respaldo NO
-- cierra esa carrera.
--
-- fk_solicitud_idempotente_orden referencia orden_id contra el indice unico
-- uq_orden_id (no hace falta arrastrar la columna fecha para el FK, aunque la
-- PK real de pedidos.orden sea compuesta (fecha, orden_id)).


-- -------------------------------------------------------------------------
-- Escrituras no idempotentes encontradas en pedidos-service (analisis del
-- 2026-08-17). Solo se corrigio checkout (la critica, ver arriba); estas
-- otras quedan documentadas para que el equipo decida cuando priorizarlas.
-- -------------------------------------------------------------------------

-- 1) CarritoService.agregarProducto (POST /api/carrito/{carritoId}/agregar)
--    NO idempotente: usa
--      INSERT INTO pedidos.carrito_detalle (...) VALUES (...)
--      ON CONFLICT (carrito_id, producto_id)
--      DO UPDATE SET cantidad = pedidos.carrito_detalle.cantidad + EXCLUDED.cantidad
--    Es un INCREMENTO, no un SET absoluto. Escenario concreto: el usuario pide
--    agregar 2 unidades del producto X; la respuesta se pierde por timeout
--    pero el UPDATE ya hizo commit (cantidad=2); el cliente reintenta el mismo
--    POST; el segundo INSERT ON CONFLICT vuelve a sumar 2 -> cantidad final=4,
--    no 2. Se necesitaria la misma idea de Idempotency-Key (o cambiar la
--    semantica del endpoint a "set absoluto") para cerrarlo.

-- 2) CarritoService.crearCarrito (usado por GET /api/carrito/{usuarioId} cuando
--    no hay carrito activo)
--    NO idempotente en si misma (cada llamada hace
--      INSERT INTO pedidos.carrito_de_compra (...) VALUES (?, 0, true) RETURNING carrito_id
--    y crea una fila nueva), pero el controller la protege con un
--    check-then-act: solo llama a crearCarrito si obtenerCarritoActivo no
--    encontro nada. Ventana TOCTOU real pero de bajo riesgo: dos peticiones
--    concurrentes del mismo usuario, ambas sin carrito activo todavia, podrian
--    pasar el chequeo antes de que la otra inserte, y terminar creando 2
--    carritos "activos" para el mismo usuario_id (no hay UNIQUE INDEX sobre
--    usuario_id WHERE habilitado=true que lo impida).

-- Para referencia, estas SI son retry-safe tal como estan (no requieren
-- cambios): CarritoService.quitarProducto (DELETE de fila ya borrada = no-op),
-- CarritoService.actualizarCantidad (SET con valor absoluto), y
-- MetodoPagoService.actualizar/cambiarEstado (SET con valores absolutos).
-- MetodoPagoService.agregar tampoco es estrictamente idempotente, pero antes
-- de insertar valida que no exista ya una tarjeta igual para el usuario, asi
-- que un reintento con los mismos datos falla fuerte (400) en vez de duplicar
-- en silencio.
