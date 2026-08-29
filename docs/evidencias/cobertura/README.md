# Evidencia de pruebas automatizadas - Paso 1

La cobertura se mide exclusivamente sobre la capa de lógica de negocio. Los adaptadores JDBC, controladores y configuración de framework quedan fuera de esta métrica.

| Microservicio | Pruebas | Líneas cubiertas | Cobertura |
|---|---:|---:|---:|
| inventario-service | 10 | 8/8 | 100.00% |
| productos-service | 14 | 23/23 | 100.00% |
| ordenes-proveedores-service | 6 | 47/51 | 92.16% |
| ventas-service | 4 | 27/27 | 100.00% |
| pedidos-service | 48 | 104/129 | 80.62% |
| usuarios | 27 | 260/343 | 75.80% |
| armado-ia | 27 | 405/540 | 75.00% |

Los archivos `*-jacoco.xml` y `armado-ia-coverage.xml` son los reportes consumibles por Codecov o SonarCloud. La prueba `GatewayIntegrationTest` agrega tres flujos HTTP reales a través del API Gateway hacia productos, usuarios y pedidos.

## Fuera del alcance

- Adaptadores de persistencia y bases de datos, salvo las pruebas de integración ya existentes con CockroachDB.
- SPA React y pruebas instrumentadas Android.
- Pruebas de contrato Pact.
- Pruebas de carga con Locust, correspondientes al Paso 3.

La publicación externa en Codecov debe hacerse desde CI sobre un commit identificable. No se realizó una carga manual porque esta ejecución se solicitó sin crear commits.
