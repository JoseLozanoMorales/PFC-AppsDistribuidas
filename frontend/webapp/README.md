# TiendaTech Webapp

Nueva interfaz de TiendaTech construida con Vue 3, TypeScript y Vite. Convive con las páginas HTML existentes y se publica bajo `/app/`.

## Desarrollo

Con el gateway ejecutándose en el puerto 8080:

```powershell
npm install
npm run dev
```

Vite abre la aplicación en `http://localhost:5173` y redirige las solicitudes `/api`, `/auth` y `/uploads` al gateway.

## Producción

```powershell
npm run build
```

El contenedor del módulo `frontend` ejecuta este paso automáticamente y empaqueta el resultado en la aplicación Spring Boot. La interfaz queda disponible en `http://localhost:8080/app/`.
