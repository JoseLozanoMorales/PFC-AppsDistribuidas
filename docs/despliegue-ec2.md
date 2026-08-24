# Despliegue de TiendaTech en EC2

## Estado

`docker-compose.prod.yml` ejecuta imágenes previamente publicadas en Docker Hub. No compila código en EC2 y no constituye todavía despliegue continuo: la publicación de imágenes y la actualización automática del servidor requieren un workflow separado.

## Convención de imágenes

Por defecto se usa el repositorio `josemoralito/tiendatech` con una etiqueta por servicio:

```text
productos-latest
inventario-latest
ventas-latest
usuarios-latest
ordenes-proveedores-latest
armado-ia-latest
pedidos-latest
frontend-latest
```

`DOCKERHUB_REPOSITORY` e `IMAGE_TAG` permiten cambiar el repositorio o desplegar una versión inmutable, por ejemplo `IMAGE_TAG=38bf21e`.

## Archivos privados del servidor

En `/opt/tiendatech` deben existir:

- `docker-compose.prod.yml`, obtenido del repositorio.
- `.env`, creado directamente en EC2 con permisos `600`.
- `crdb-certs/`, directorio privado con `ca.crt`, el certificado del cliente y su clave PK8 cuando `CRDB_DATASOURCE_URL` usa `verify-ca`.

El `.env` debe contener la conexión de CockroachDB, credenciales de correo y el mismo `AUTH_JWT_SECRET` para Usuarios, Gateway y Armado IA. No debe copiarse al repositorio ni incluirse dentro de ninguna imagen.

La ruta anfitriona de certificados se configura con `CRDB_CERTS_DIR=./crdb-certs`. Compose la monta como `/app/crdb-certs` en modo de solo lectura, que debe coincidir con las rutas `sslrootcert`, `sslcert` y `sslkey` de la URL JDBC. Las claves deben conservar permisos restrictivos y nunca publicarse en Docker Hub o GitHub.

## Inicio manual

Desde `/opt/tiendatech`:

```text
docker compose --env-file .env -f docker-compose.prod.yml config --quiet
docker compose --env-file .env -f docker-compose.prod.yml pull
docker compose --env-file .env -f docker-compose.prod.yml up -d
docker compose --env-file .env -f docker-compose.prod.yml ps
```

El único puerto publicado por Compose es el Gateway, `8180` por defecto. Los puertos de los microservicios permanecen dentro de la red Docker.

## Verificación

```text
curl --fail http://localhost:8180/actuator/health
curl --fail "http://localhost:8180/api/productos?page=0&size=1"
```

Para uso real desde Android se necesita un dominio con HTTPS delante del Gateway. No debe configurarse el APK release con credenciales JDBC ni con direcciones internas de Docker.

## Actualización y reversión

Para desplegar una versión publicada:

```text
IMAGE_TAG=<commit> docker compose --env-file .env -f docker-compose.prod.yml pull
IMAGE_TAG=<commit> docker compose --env-file .env -f docker-compose.prod.yml up -d
```

La reversión consiste en repetir ambos comandos con la etiqueta del commit anterior. No se recomienda depender únicamente de `latest` en producción.
