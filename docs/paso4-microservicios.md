# Paso 4 — recuperación y endurecimiento de microservicios

En este proyecto, “base de datos propia” se interpreta como **esquema propio** dentro de
CockroachDB. Cada microservicio debe limitar su SQL a su esquema; los datos de otro
servicio se obtienen mediante su contrato HTTP/gRPC o eventos.

## Avance integrado

- Gateway con límite local por IP de transporte, ventana configurable y máximo de clientes.
- Respuesta `429` con `Retry-After` y envoltura `{status, data, message, timestamp}`.
- Registro de marca de tiempo, método, ruta, origen y estado, sin cuerpos ni credenciales.
- Casos JWT sin token, válido, caducado y malformado.
- Variables documentadas en `.env.example` y Compose.
- Auditoría estática ejecutable con `python3 scripts/audit_paso4.py`.
- SQL limitado al esquema propietario; “más vendidos” usa HTTP Productos→Ventas.
- Respuestas JSON uniformes mediante advice/middleware transversal.
- Validación HS256 y expiración en Gateway y en cada microservicio.
- Siete contratos OpenAPI 3.0 en `docs/api/`.
- Compose local autocontenido con tres nodos CockroachDB e inicialización del esquema.
- Migración V006 aplicada en el clúster desplegado: cero claves foráneas entre esquemas.
- Comprobación obligatoria realizada desde un clon independiente: las ocho imágenes se
  construyeron y el sistema completo arrancó con el comando documentado en el README.

## Límites conocidos

El limitador vive en memoria por instancia y no debe considerarse distribuido. Detrás de
Caddy usa la IP del par de transporte y no confía en `X-Forwarded-For` suministrado por el
cliente. Para varias réplicas se necesita almacenamiento compartido o limitación en el proxy.

La auditoría estática señala candidatos y complementa, pero no reemplaza, las pruebas en
ejecución. El token interno entre servicios debe ser distinto del secreto JWT y rotarse como
secreto de despliegue.

## Validación

```bash
python3 scripts/audit_paso4.py
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example -f docker-compose.prod.yml config --quiet
python3 -c 'import glob,json; [json.load(open(f)) for f in glob.glob("docs/api/*.yaml")]'
docker run --rm --mount "type=bind,source=$PWD/Apps/web/frontend,target=/app" \
  -w /app maven:3.9-eclipse-temurin-17 \
  mvn -B '-Dtest=GatewayTrafficFilterTest,JwtGatewayFilterTest' test
```

La validación reproducible del 1 de septiembre de 2026 obtuvo además:

- `GET /actuator/health`: HTTP 200 y estado `UP`;
- `POST /api/productos` sin token: HTTP 401 y envoltura uniforme;
- `GET /api/productos`: HTTP 200 y envoltura uniforme;
- `cockroach node status`: tres nodos disponibles y vivos.
