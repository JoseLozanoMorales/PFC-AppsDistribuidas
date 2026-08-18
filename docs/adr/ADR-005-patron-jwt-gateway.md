# ADR-005: Patrón reutilizable de validación JWT en el gateway

## Estado

Aceptado.

## Contexto

Los microservicios de TiendaTech se comunican dentro de la red interna de Docker. El cliente externo no debe llamar directamente a los servicios `productos`, `inventario`, `pedidos`, `ordenes-proveedores`, `usuarios` ni `ventas`; debe entrar por el gateway web.

El servicio de usuarios es responsable de autenticar credenciales y emitir tokens JWT. El gateway es responsable de validar esos tokens antes de reenviar tráfico a los microservicios internos.

## Decisión

Se adopta este patrón para todos los microservicios:

1. `usuarios-service` valida login y firma el JWT con `AUTH_JWT_SECRET`.
2. El cliente llama al gateway con `Authorization: Bearer <token>`.
3. El gateway valida firma, expiración y claims del JWT.
4. Si el token es válido, el gateway reenvía la petición al microservicio interno.
5. El gateway sobrescribe cabeceras de identidad confiables:
   - `X-User-Id`
   - `X-Usuario`
   - `X-User-Role`
6. Los microservicios internos consumen esas cabeceras solo porque el tráfico llega desde la red interna y no desde el host.

## Reglas de seguridad

- Los puertos de microservicios internos no se publican al host.
- Solo el gateway queda publicado para tráfico HTTP de la aplicación.
- Un microservicio no debe confiar en cabeceras `X-User-*` si también está expuesto directamente al host.
- Si en el futuro un microservicio se expone directamente, debe validar JWT por sí mismo o volver a quedar detrás del gateway.
- El gateway debe eliminar o sobrescribir cabeceras de identidad recibidas del cliente externo antes de reenviar la petición.

## Rutas públicas

Las rutas públicas no requieren JWT. Ejemplos:

- Login
- Registro de usuario
- Recuperación de contraseña
- OTP
- Catálogos públicos necesarios para registro
- Recursos estáticos
- Actuator health

## Rutas protegidas

Las rutas protegidas requieren JWT válido. Para incorporar otro microservicio al patrón:

1. Registrar sus rutas privadas en la configuración del gateway.
2. Mantener el microservicio sin `ports:` en `docker-compose.yml`.
3. Reenviar tráfico usando el nombre interno del servicio Docker, por ejemplo `http://productos-service:8081`.
4. Leer identidad desde `X-User-Id`, `X-Usuario` y `X-User-Role` cuando el endpoint necesite usuario autenticado.

## Ejemplo de consumo en un controlador Spring

```java
@GetMapping("/recurso-privado")
public ResponseEntity<?> recursoPrivado(
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-Usuario") String usuario,
        @RequestHeader("X-User-Role") String rol) {
    // Usar userId, usuario y rol como identidad ya validada por el gateway.
    return ResponseEntity.ok().build();
}
```

## Consecuencias

- La validación JWT queda centralizada en el gateway.
- Se evita duplicar lógica de seguridad en todos los microservicios.
- Los microservicios quedan protegidos por aislamiento de red Docker.
- El patrón es reutilizable: para proteger nuevos endpoints se actualiza el gateway y se mantiene cerrado el puerto del servicio al host.
