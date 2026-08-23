# ADR-007: Patrón reutilizable de validación JWT en el gateway

## Estado

Aceptado y ratificado el 17 de agosto de 2026.

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

Como regla general, el gateway protege `/api/**`. Las excepciones públicas se
declaran explícitamente. Para el catálogo se permiten únicamente solicitudes
`GET` y `HEAD`; usar la misma ruta con `POST`, `PUT`, `PATCH` o `DELETE` exige
JWT.

## Comunicación entre microservicios

- Los servicios internos no vuelven a validar el JWT: el punto de confianza es
  el gateway y el aislamiento de la red Docker.
- Una llamada interna necesaria para completar una operación, por ejemplo
  `pedidos-service` hacia `ventas-service`, no debe fallar por ausencia de JWT.
- Si el servicio receptor necesita identidad, el emisor propaga las cabeceras
  `X-User-*` que recibió del gateway. No acepta valores originados directamente
  desde un cliente externo.
- Los endpoints técnicos que deban ser invocados solo entre servicios deben
  documentarse como tales. Si en el futuro se requiere una frontera de confianza
  más fuerte, se adoptará autenticación de servicio a servicio en un ADR nuevo,
  sin mezclarla con el JWT del usuario.

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
