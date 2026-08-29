# Bitácora de ejecución ISO 25010 — 2026-08-28

Commit evaluado: `9c618a2` (`main`). Zona horaria: `America/Guayaquil`.

## Stack y métricas operativas

`docker compose config --services` resolvió los nueve servicios esperados. Al
ejecutar `docker compose ps -a`, el daemon no estaba disponible:

```text
failed to connect to the docker API at unix:///var/run/docker.sock;
connect: no such file or directory
```

Por esta razón no se inició una corrida parcial: uptime de una hora, P95, tasa
5xx y captura del dashboard se conservaron como `PENDIENTE`.

## Complejidad

PMD 7.17.0 se ejecutó sobre todo `src/main/java` de los siete módulos. Los XML
originales y `summary.csv` están en `complejidad/`. El máximo por método fue 20
en `JdbcInventarioRepository.registrarItem`; el objetivo estricto era `<10`.

## Cobertura

Se analizaron los contadores `LINE` raíz de los seis XML JaCoCo existentes en
`docs/evidencias/cobertura/`. El mínimo medido fue 75.80 % (`usuarios`). El
equipo suministró además la evidencia de que `armado-ia` supera 70 %, pero el
`coverage.xml` original de Python no está presente en este checkout; esto queda
declarado en `cobertura-summary.csv` y debe adjuntarse antes de empaquetar la
entrega final.

## Seguridad

La evidencia suministrada indica 11 casos y 0 fallos, incluidos 8/8 casos
parametrizados de familias protegidas que retornan 401 sin JWT. El código se
encuentra en `GatewayIntegrationTest.java`. No se pudo regenerar Surefire en
este entorno porque solo está instalado el JRE 25, sin `javac`; Maven terminó
con `release version 17 not supported`. El XML original de Surefire debe
adjuntarse al paquete final si se requiere evidencia autocontenida.
