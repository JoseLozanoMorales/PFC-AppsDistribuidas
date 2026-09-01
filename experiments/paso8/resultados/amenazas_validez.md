# Amenazas a la validez - Paso 8

## Interna

- El banco se ejecuta en una sola maquina y comparte CPU con el sistema operativo.
- La ejecucion local usa `warmup_seconds=0.0` y `delay_seconds=0.05`. La configuracion de rubrica queda parametrizada, pero la evidencia local puede acelerar el retardo para caber en la ventana disponible.
- SQLite modela los datos aislados de Inventario, Pagos y Ordenes; no sustituye una medicion de red real entre microservicios.

## Externa

- Los casos son sinteticos y concentran compradores sobre un producto para forzar contencion; no representan toda la variedad de una tienda real.
- Los niveles 50, 100, 200 y 400 usuarios simulan simultaneidad del checkout, no trafico mixto de navegacion, catalogo y administracion.

## De Constructo

- La inconsistencia observable se operacionaliza como violaciones del oraculo: pago exacto, descuento unico, stock nunca negativo y compensacion completa.
- La convergencia de compensacion usa eventos internos del banco; no incluye latencia de una pasarela de pagos real.

## De Conclusion

- Se usan cinco repeticiones por condicion; los intervalos son informativos, pero siguen siendo sensibles al ruido local.
- Mann-Whitney U y A12 comparan tendencia de latencias entre 2PC y Saga; no prueban causalidad fuera del banco definido.
