# Validación inicial del esquema distribuido

- Fecha: 2026-07-28
- Motor: CockroachDB v23.2.4
- Imagen: `cockroachdb/cockroach:v23.2.4`
- Entorno: nodo temporal aislado, almacenamiento en memoria
- Archivo validado: `docs/db/schema.sql`

## Resultado

El esquema se ejecutó completamente sin errores en CockroachDB 23.2.4:

- base `tiendatech` creada;
- esquemas `usuarios` y `pedidos` creados;
- tablas `usuarios.usuario`, `pedidos.orden` y
  `pedidos.detalle_orden` creadas;
- cinco límites temporales aplicados a cada tabla de pedidos;
- rangos distribuidos mediante `SCATTER`;
- configuración predeterminada establecida en `num_replicas = 3`.

Los límites corresponden a:

1. 2026-01-01;
2. 2026-04-01;
3. 2026-07-01;
4. 2026-10-01;
5. 2027-01-01.

La consulta `SHOW RANGES FROM TABLE pedidos.orden WITH DETAILS` devolvió seis
intervalos físicos, incluyendo el rango anterior al primer límite y el
posterior al último. `SHOW ZONE CONFIGURATION FOR RANGE default` confirmó:

```text
num_replicas = 3
```

## Hallazgo durante la validación

`PARTITION BY RANGE` fue rechazado porque requiere una licencia Enterprise en
CockroachDB 23.2.4. Para conservar un despliegue reproducible con la imagen
comunitaria, la fragmentación trimestral se implementó mediante límites
explícitos `SPLIT AT` y distribución `SCATTER`. La decisión está documentada en
`docs/adr/ADR-003-fragmentacion-pedidos.md`.

## Alcance de esta evidencia

La prueba confirma compatibilidad sintáctica, creación de rangos y configuración
del factor de replicación. No demuestra todavía consenso ni tolerancia a fallos,
porque se ejecutó sobre un único nodo temporal. Esas propiedades deben validarse
posteriormente en el cluster de tres nodos exigido por la Entrega 3.
