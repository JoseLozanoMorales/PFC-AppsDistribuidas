# Evidencia de cierre H10 - Reglas de Productos versionadas

## Hallazgo original

La revisión técnica ACC indicó que `ProductoService` dependía de tres funciones
de base de datos cuyas definiciones no estaban versionadas en el repositorio:

- `productos.productos_mas_vendidos_menu`
- `public.f_productos_recientes_con_imagen_menu`
- `public.fn_listar_categorias`

Esto impedía entender y reproducir esos comportamientos leyendo únicamente el
código versionado del microservicio.

## Corrección verificada

Los tres comportamientos están implementados actualmente mediante consultas SQL
explícitas dentro de
`services/productos-service/src/main/java/com/example/productos/service/ProductoService.java`:

| Comportamiento | Método actual | Tablas consultadas |
|---|---|---|
| Productos más vendidos | `masVendidos` | `ventas.factura_cuerpo`, `productos.producto` |
| Productos recientes del menú | `recientesMenu` | `productos.producto`, `productos.galeria_productos_v2` |
| Categorías habilitadas | `categorias` | `productos.categoria_producto` |

El microservicio activo ya no invoca ninguna de las tres funciones señaladas por
ACC. La lógica de lectura, filtros, orden y límites queda visible en Java y se
revisa junto con el resto del código fuente.

## Prueba automática

`ProductoServiceH10Test` ejecuta los tres métodos con un `JdbcTemplate` simulado
y captura la sentencia entregada a la capa de datos. Cada caso comprueba que:

1. no aparece el nombre de la función de base de datos anterior;
2. la consulta referencia directamente las tablas esperadas;
3. el flujo de categorías conserva su transformación de salida.

La prueba se ejecuta con:

```text
mvn test
```

desde `services/productos-service`.

## Criterio de cierre

H10 puede cerrarse cuando la prueba del módulo termina correctamente y una
búsqueda dentro de `services/productos-service` no encuentra referencias a las
tres funciones originales.
