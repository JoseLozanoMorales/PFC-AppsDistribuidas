# TiendaTech Webapp

Nueva interfaz de TiendaTech construida con React 18, TypeScript y Vite. Convive con las páginas HTML existentes y se publica bajo `/app/`.

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

Los contenedores `frontend` y `tiendatech-gateway` ejecutan este paso automáticamente y empaquetan el resultado en la aplicación Spring Boot. En el entorno distribuido la interfaz queda disponible en `http://localhost:8180/app/`.
