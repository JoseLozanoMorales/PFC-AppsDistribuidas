# Especificación de construcción de la aplicación móvil TiendaTech

## 1. Propósito del documento

Este documento es la fuente de verdad para construir la aplicación Android de **AGLS - TiendaTech**. Está pensado para que una IA de Android Studio pueda implementar la aplicación por etapas sin inventar requisitos, endpoints ni respuestas del backend.

Antes de escribir código, la IA debe leer este archivo completo y revisar los archivos del repositorio citados en cada sección. Si el código real contradice este documento, debe detenerse, explicar la diferencia y proponer la corrección mínima. No debe modificar el backend ni la aplicación web sin autorización explícita.

## 2. Fuentes revisadas

- Guía de la Entrega 4: `PFC_ENTREGA_FINAL/archivo20267771030.pdf`.
- Frontend React actual: `Apps/web/frontend/webapp/src/`.
- Configuración del API Gateway: `Apps/web/frontend/src/main/resources/application.yml`.
- Validación JWT del Gateway: `Apps/web/frontend/src/main/java/com/tiendatech/frontend/security/JwtGatewayFilter.java`.
- Microservicios actuales bajo `services/`.
- Proyecto Android generado bajo `Apps/mobile/`.

La interfaz móvil debe conservar la identidad visual y el comportamiento del flujo de comprador del frontend React, pero debe adaptarlos a patrones móviles; no debe copiar literalmente una página de escritorio.

## 3. Conclusión de alcance

La aplicación móvil es exclusivamente un **cliente de compra**. Consume la misma API distribuida que la aplicación web a través del API Gateway.

### Incluido

- Registro de clientes con verificación OTP.
- Inicio y cierre de sesión.
- Recuperación de contraseña.
- Catálogo, categorías, búsqueda local y actualización manual.
- Detalle y galería del producto.
- Carrito: agregar, consultar, cambiar cantidad y eliminar.
- Perfil y direcciones del cliente.
- Métodos de pago.
- Checkout real mediante el endpoint existente.
- Historial y detalle de pedidos.
- Consulta de facturas.
- Caché local del catálogo y experiencia básica sin conexión.
- Escaneo de códigos con cámara.
- Notificaciones push de estado del pedido cuando exista soporte en el backend.
- Tema claro y oscuro.
- Pruebas unitarias, de integración de datos y al menos una prueba instrumentada E2E.

### Excluido

- Panel de vendedor o trabajador.
- Administración de usuarios.
- Creación, edición o eliminación administrativa de productos.
- Inventario administrativo.
- Dashboard de métricas y observabilidad.
- Armado de PC asistido por IA, salvo una ampliación posterior explícita.
- Funciones exclusivas de los roles ADMIN y TRABAJADOR.

Si un usuario con rol distinto de CLIENTE inicia sesión, la app debe mostrar un mensaje indicando que esta aplicación es solo para compradores y cerrar esa sesión. No debe mostrar pantallas administrativas.

## 4. Estado actual del proyecto Android

La raíz correcta del proyecto es `Apps/mobile/` y el módulo Android es `Apps/mobile/app/`. No se debe crear otra carpeta `app` ni mover el wrapper de Gradle.

La plantilla actual usa Kotlin, Jetpack Compose y Material 3, pero todavía conserva valores de ejemplo:

- `rootProject.name = "My Application"`.
- `namespace` y `applicationId`: `com.example.myapplication`.
- `minSdk = 24`.
- `targetSdk = 36` y `compileSdk = 36.1`.
- Tema y pantalla `Greeting` de la plantilla.
- JUnit 4 como dependencia inicial.

### Cambios iniciales obligatorios

1. Cambiar el nombre del proyecto y de la aplicación a `TiendaTech`.
2. Cambiar `namespace` y `applicationId` a `com.tiendatech.mobile`.
3. Refactorizar los paquetes Kotlin desde `com.example.myapplication` a `com.tiendatech.mobile`.
4. Cambiar `minSdk` a 26, como exige la guía.
5. Se puede conservar `targetSdk = 36` y `compileSdk = 36.1`; son superiores al objetivo 34 indicado en la guía. Documentar esta decisión en el ADR móvil.
6. Usar Java 17 para el toolchain de Kotlin/Android salvo incompatibilidad demostrada.
7. Eliminar la pantalla de ejemplo solo cuando exista el primer flujo navegable.
8. Añadir permiso `INTERNET`.
9. Añadir permiso `CAMERA`, solicitado en tiempo de ejecución únicamente al abrir el escáner.
10. Añadir permiso `POST_NOTIFICATIONS` para Android 13+, solicitado cuando la función de pedidos lo justifique.
11. Desactivar el backup de credenciales o excluir explícitamente tokens y datos sensibles de las reglas de backup.

No introducir secretos, contraseñas, claves JWT, credenciales de correo ni claves privadas en el repositorio.

### Estado de implementación verificado

- Fase 0: completada y verificada.
- Fase 1A (núcleo de dependencias, configuración y red): completada el 20 de agosto de 2026.
- Hilt está integrado mediante `TiendaTechApplication`, `@AndroidEntryPoint` y módulos singleton.
- Kotlin Serialization, Retrofit y OkHttp están configurados contra una única URL base.
- Debug usa por defecto `http://10.0.2.2:8180/`, puerto publicado por el Gateway del `docker-compose.yml` conectado a CockroachDB; puede sobrescribirse con `TIENDATECH_DEBUG_API_BASE_URL`. Release lee `TIENDATECH_API_BASE_URL` y usa un dominio inválido seguro si no se configura.
- El tráfico HTTP claro está permitido únicamente en la variante debug para `10.0.2.2` y `localhost`.
- El interceptor Bearer consume `SessionTokenProvider` y no envía cabeceras manuales de identidad.
- Existe clasificación común de errores HTTP, conexión, timeout y fallos inesperados.
- El servicio diagnóstico utiliza la ruta pública real `GET /api/productos?page=0&size=1`.
- Verificación de Fase 1A: lint sin errores, pruebas unitarias aprobadas y APK debug generado.
- Fase 1B (persistencia y componentes comunes): completada el 20 de agosto de 2026.
- La sesión JWT se conserva cifrada con AES-GCM y una clave no exportable de Android Keystore; no se almacena el token en texto plano.
- DataStore conserva preferencias no sensibles, comenzando por el modo de tema (`SYSTEM`, `LIGHT` o `DARK`).
- Room dispone de esquema versionado para la caché local de productos, categorías y metadatos, con sus DAO y módulo de inyección.
- Existen componentes Compose reutilizables para carga, error con reintento, estado vacío y acción principal.
- Verificación acumulada de Fase 1: lint sin errores, 16 pruebas JVM aprobadas, APK debug generado y prueba instrumental de Room compilada. La prueba instrumental requiere un emulador o dispositivo para ejecutarse.
- Fase 2 (autenticación de clientes): completada el 20 de agosto de 2026.
- Se implementaron login, restauración de sesión mediante `GET /api/usuarios/me`, cierre de sesión, registro OTP en dos pasos y recuperación de contraseña.
- La aplicación rechaza cuentas con roles administrativos o de trabajador y solo conserva tokens de clientes (`id_rol = 2`).
- La restauración elimina el token cifrado cuando el backend responde `401` o `403`; un fallo de conexión no destruye la credencial almacenada.
- La navegación permite catálogo público como invitado y concentra las rutas de login, registro y recuperación. Las funcionalidades privadas futuras deben consultar el estado central de sesión antes de abrirse.
- Verificación acumulada hasta Fase 2: lint sin errores, 23 pruebas JVM aprobadas, APK debug y APK instrumental generados. La ejecución instrumental de Room continúa requiriendo un emulador o dispositivo.
- Fase 3 (catálogo móvil): completada el 20 de agosto de 2026.
- El catálogo público consume productos y categorías reales, permite búsqueda local, filtros por categoría y actualización manual.
- Al seleccionar una categoría se consulta `GET /api/productos/por-categoria` para completar la relación de categoría que no viene incluida en el resumen general del backend.
- El detalle consume `GET /api/productos/{id}` y la galería real de `GET /api/galeria_v2/producto/{id}?scope=galeria`.
- Productos y categorías se conservan en Room. Ante un fallo de red, se muestran los datos guardados y el error sin borrar la caché válida.
- Las imágenes utilizan Coil 3.4.0 con caché de memoria y disco. No se usa Coil 3.5.0 porque fue compilado con Kotlin 2.4 y es incompatible con Kotlin 2.2.10, versión estable actual del proyecto.
- Los productos deshabilitados no se almacenan ni muestran; los agotados aparecen visibles pero sin posibilidad de compra.
- El botón “Añadir al carrito” permanece explícitamente desactivado hasta implementar la Fase 4, evitando simular una compra inexistente.
- Verificación acumulada hasta Fase 3: lint sin errores, 27 pruebas JVM aprobadas y ambos APK debug generados. Las 17 advertencias restantes son avisos de versiones disponibles mantenidas por compatibilidad.
- Fase 4 (carrito de compras): completada el 21 de agosto de 2026.
- Se implementó la obtención o creación del carrito activo, listado paginado de líneas, agregado de productos, modificación de cantidades y eliminación.
- La aplicación nunca envía precios al agregar o modificar productos. Los subtotales y el total visual se calculan desde `precioUnitario`, valor confiable devuelto por el servicio de pedidos.
- El carrito exige una sesión de cliente. Las respuestas `401` y `403` cierran la sesión local y llevan nuevamente al login.
- Si un invitado intenta añadir un producto o abrir el carrito, se conserva la ruta pendiente y, después del login, se regresa al producto o carrito correspondiente.
- La cantidad no puede superar el stock conocido. Reducir una línea hasta cero utiliza el comportamiento real del backend para quitarla.
- El carrito reutiliza la caché del catálogo para mostrar nombres e imágenes sin duplicar información privada en Room.
- El paso de pago permanece explícitamente desactivado hasta la siguiente fase; no se simula una orden ni un cobro inexistentes.
- Verificación acumulada hasta Fase 4: lint sin errores, 31 pruebas JVM aprobadas y APK principal e instrumental generados. Las 17 advertencias restantes continúan siendo únicamente avisos de versiones disponibles.
- Fase 5 (cuenta y checkout): completada el 21 de agosto de 2026.
- La cuenta muestra el perfil real, permite crear, editar y eliminar direcciones y consulta ciudades y provincias para el formulario.
- Los métodos de pago permiten listar máscaras, crear, actualizar, inactivar y reactivar. El formulario no solicita CVV y aclara que el mecanismo actual es académico, no una pasarela PCI real.
- El número completo de tarjeta y las contraseñas permanecen únicamente en memoria durante el formulario, se limpian tras una operación correcta y nunca se escriben en Room, DataStore, preferencias o logs.
- Se implementó cambio de contraseña y selector persistente de tema (`Sistema`, `Claro`, `Oscuro`) mediante DataStore.
- El checkout exige carrito no vacío, dirección habilitada y método de pago habilitado, muestra el resumen y solicita confirmación explícita.
- Cada intención de checkout utiliza una `Idempotency-Key` UUID que se conserva mientras el resultado sea ambiguo. No se genera una clave nueva para un reintento incierto.
- Si se pierde la respuesta o el servidor falla después de crear la orden, la aplicación consulta el historial y reconoce una orden nueva antes de mostrar éxito. Si no puede comprobarlo, bloquea otro intento y advierte que no se repita la compra.
- La pantalla de éxito muestra únicamente número de orden, fecha y total; el historial y detalle completo corresponden a la Fase 6.
- Verificación acumulada hasta Fase 5: lint sin errores, 36 pruebas JVM aprobadas y APK principal e instrumental generados. Las 17 advertencias restantes son únicamente avisos de versiones disponibles mantenidas por compatibilidad.
- Fase 6 (pedidos y facturas): completada el 21 de agosto de 2026.
- El historial consulta exclusivamente las órdenes del cliente autenticado mediante `GET /api/ordenes/usuario/{usuarioId}` y pagina los resultados sin cargar nuevamente las páginas ya incorporadas.
- La interfaz muestra el texto neutral `Orden registrada`, porque el contrato actual no proporciona un campo de estado ni transiciones del pedido. No se inventan estados como enviado, entregado o cancelado.
- El detalle obtiene la orden y sus líneas desde los endpoints reales. Cada línea presenta cantidad, precio unitario, subtotal, IVA y total; el nombre del producto se completa desde la caché pública del catálogo cuando está disponible.
- Las facturas se consultan con `GET /api/facturas?usuarioId={usuarioId}` y se relacionan con la orden por `ordenId`. La aplicación solo solicita el detalle de una factura cuyo identificador apareció previamente en esa lista filtrada del cliente, compensando la ausencia de validación de pertenencia observada en el endpoint individual del backend.
- Cuando una orden todavía no tiene factura se muestra un estado explícito y válido, sin tratarlo como error. Cuando existe, se presentan número, fecha de emisión, datos de entrega y líneas facturadas.
- La aplicación informa que la API actual no ofrece descarga PDF; no genera enlaces ni documentos ficticios.
- Las rutas de pedidos y detalle están protegidas por la sesión central. La cuenta abre el historial y la pantalla de éxito del checkout puede abrir directamente la orden recién creada.
- Verificación acumulada hasta Fase 6: lint sin errores, 41 pruebas JVM aprobadas y APK principal e instrumental generados. Las 17 advertencias restantes son únicamente avisos de versiones disponibles mantenidas por compatibilidad.
- Fase 7 (cámara y lectura de códigos): completada en el alcance móvil posible el 21 de agosto de 2026.
- Se integraron CameraX 1.6.1 y ML Kit Barcode Scanning 17.3.0. El analizador acepta EAN-8, EAN-13, UPC-A, UPC-E, Code 128 y QR, procesa únicamente el frame más reciente y siempre libera cada imagen analizada.
- El acceso `Escanear código` está disponible desde el catálogo. La app explica el uso de la cámara y solicita `CAMERA` únicamente al entrar en esa función; la cámara sigue siendo opcional para instalar y usar el resto de la aplicación.
- Después de una detección, el análisis se pausa para evitar lecturas duplicadas. El usuario puede reactivar la cámara y también escribir un código manualmente.
- La búsqueda está desacoplada mediante `ProductLookupByBarcode`. Su implementación actual devuelve `BackendUnavailable` de forma explícita porque el backend no ofrece un campo ni endpoint de código de barras; no se interpreta `enlace` ni otro campo como sustituto.
- La pantalla confirma el código leído y explica la limitación del catálogo. La ruta para abrir un producto ya está preparada para el resultado `Found`, pero solo podrá activarse con una implementación respaldada por un contrato real.
- Las pruebas unitarias cubren normalización, validación, bloqueo de duplicados, producto encontrado y reactivación del escáner. La cámara real requiere una comprobación manual o instrumental en un dispositivo o emulador con cámara.
- Verificación acumulada hasta Fase 7: lint sin errores, 46 pruebas JVM aprobadas y APK principal e instrumental generados. Las 17 advertencias restantes continúan siendo únicamente avisos de versiones disponibles mantenidas por compatibilidad.
- Fase 8 (infraestructura de notificaciones): completada en el alcance local posible el 21 de agosto de 2026.
- La aplicación crea al iniciar el canal Android `pedidos` y solicita `POST_NOTIFICATIONS` en Android 13 o superior únicamente cuando el cliente decide habilitar la demostración.
- Existe un modelo independiente de Firebase para validar y mapear el payload mínimo (`ordenId`, título y mensaje). Los payloads sin una orden positiva se descartan.
- Las notificaciones abren `tiendatech://orders/{orderId}`. El deep link atraviesa la navegación protegida y el repositorio vuelve a comprobar que la orden pertenece al cliente autenticado antes de presentar sus datos.
- La sección `Notificaciones` de Cuenta permite emitir una notificación local claramente rotulada como demostración; no se presenta como push remoto ni como cambio real del estado del pedido.
- Se añadieron pruebas unitarias del payload y pruebas instrumentadas compilables para comprobar la creación del canal y la resolución del deep link. La ejecución real requiere emulador o dispositivo.
- FCM continúa correctamente bloqueado: no se añadieron Firebase BOM, `google-services.json`, tokens inventados ni llamadas a endpoints inexistentes.
- Fase 9 (calidad y entrega): completada en su alcance automatizable local el 21 de agosto de 2026.
- Se añadió un trabajo Android a `.github/workflows/ci.yml` que ejecuta lint, pruebas JVM y compilación de los APK principal e instrumental, publica ambos APK y conserva reportes cuando falla.
- Se creó `Apps/mobile/README.md` con requisitos, URL debug, comandos de verificación, ubicación del APK y limitaciones vigentes.
- El ADR `docs/adr/002-mobile-platform.md` registra la elección de Android nativo con criterios cuantitativos y sus consecuencias.
- Se ejecutó una compilación limpia desde cero antes del cierre. Verificación acumulada final: lint sin errores, 49 pruebas JVM aprobadas y APK principal e instrumental generados. Las 17 advertencias restantes son únicamente avisos de versiones disponibles conservadas por compatibilidad.
- Las pruebas instrumentadas de Room, canal y deep link compilan, pero no se ejecutaron localmente porque esta sesión no dispone de un dispositivo o emulador iniciado. El recorrido E2E completo catálogo-login-carrito-checkout continúa siendo una validación pendiente en un entorno con backend controlado; no se declara falsamente como ejecutado.

## 5. Identidad visual tomada del frontend actual

La aplicación debe sentirse como la versión móvil de TiendaTech.

### Paleta base

- Fondo claro: `#F5F7FB`.
- Superficie clara: `#FFFFFF`.
- Texto principal claro: `#172033`.
- Texto secundario: aproximadamente `#647087` o `#758096`.
- Primario: `#5C65EE`.
- Primario alterno: `#6263DF`.
- Acento morado: `#7A4BD3` o `#814DD8`.
- Error: aproximadamente `#B62B3A`.
- Éxito: aproximadamente `#18794E`.
- Fondo oscuro: `#0D1320`.
- Superficie oscura: `#151E2E`.
- Borde oscuro: `#263349`.

### Estilo

- Material 3 con formas redondeadas de 12 a 20 dp.
- Botones primarios con degradado morado si Compose lo permite sin perjudicar accesibilidad; en caso contrario usar el color primario sólido.
- Tarjetas limpias, bordes suaves y elevación moderada.
- Títulos con peso alto y cuerpo muy legible.
- Icono/monograma `TT` como elemento de marca hasta que exista un logo definitivo.
- Soporte completo para tema claro y oscuro, guardando la preferencia localmente.
- No usar colores dinámicos del dispositivo por defecto, porque alterarían la identidad visual. Pueden habilitarse solo como opción posterior.
- Formato monetario: USD con configuración regional `es-EC`.
- Textos visibles en español; preparar recursos `strings.xml` para futura internacionalización y no incrustar textos repetidos en composables.

### Adaptación móvil

- Barra inferior recomendada: `Inicio`, `Pedidos`, `Carrito`, `Cuenta`.
- El escáner puede ser una acción visible en Inicio o en la barra de búsqueda.
- Mostrar contador del carrito en su icono.
- Usar una sola columna de contenido y tarjetas adaptativas; no reproducir la cabecera web de escritorio.
- Respetar barras del sistema, teclado, tamaños táctiles mínimos de 48 dp y lectores de pantalla.

## 6. Arquitectura obligatoria

Usar una sola aplicación Android modularizada por paquetes y características. No crear múltiples módulos Gradle en la primera versión.

```text
com.tiendatech.mobile/
  TiendaTechApplication.kt
  MainActivity.kt
  core/
    common/
    designsystem/
    navigation/
    network/
    database/
    security/
  data/
    local/
    remote/
    repository/
  domain/
    model/
    repository/
    usecase/
  feature/
    auth/
    catalog/
    product/
    scanner/
    cart/
    checkout/
    orders/
    invoices/
    account/
    settings/
```

### Reglas de arquitectura

- UI con Jetpack Compose.
- Estado de pantalla en ViewModels mediante `StateFlow` y estados inmutables.
- Flujo unidireccional: evento de UI -> ViewModel/use case -> repositorio -> nuevo estado.
- La UI no llama Retrofit, Room ni DataStore directamente.
- Repositorios definidos mediante interfaces de dominio e implementados en `data`.
- Coroutines para operaciones asíncronas.
- Inyección de dependencias con Hilt.
- Navegación con Navigation Compose y rutas tipadas si la versión elegida lo soporta de forma estable.
- Retrofit + OkHttp + Kotlin serialization para HTTP/JSON.
- Room para caché estructurada.
- DataStore para preferencias no sensibles.
- Android Keystore mediante una solución vigente para proteger el token. No usar `EncryptedSharedPreferences` si la versión instalada lo marca obsoleto; implementar almacenamiento cifrado respaldado por Keystore.
- Coil para imágenes.
- CameraX + ML Kit Barcode Scanning para cámara/códigos.
- Firebase Cloud Messaging para push, pero solo activar la integración final cuando exista el contrato del backend.

Usar versiones estables compatibles con el AGP y Kotlin ya configurados. No introducir versiones alpha, beta o RC sin justificarlo.

## 7. Configuración de red

Toda llamada debe entrar por el API Gateway. No conectar la app directamente con cada microservicio.

### URL base por entorno

- Emulador Android con el backend Docker actual: `http://10.0.2.2:8180/`.
- Dispositivo físico: `http://IP_LAN_DEL_EQUIPO:8180/`; no usar `localhost`.
- Entorno distribuido CRDB expuesto por el compose: confirmar si corresponde el puerto `8180` antes de usarlo.
- Producción: exigir HTTPS.

Definir `API_BASE_URL` mediante `BuildConfig` o propiedades por variante. No repartir URLs literales por el código.

El `.env` raíz contiene credenciales y configuración exclusiva de los microservicios. Nunca debe copiarse, leerse ni empaquetarse desde Android: la aplicación consume el Gateway REST y es el backend quien establece la conexión JDBC con la base de datos en la nube.

Para desarrollo local HTTP, usar una `network_security_config` que permita tráfico claro solo hacia los hosts de desarrollo. No habilitar `usesCleartextTraffic=true` globalmente en release.

### Cabeceras y autenticación

- Agregar `Authorization: Bearer <token>` mediante un interceptor de OkHttp en todas las rutas protegidas.
- No enviar manualmente `X-User-Id`, `X-Usuario` ni `X-User-Role`: el Gateway valida el JWT y sustituye esas cabeceras por valores confiables.
- Agregar `Accept: application/json`.
- Agregar `Content-Type: application/json` cuando exista cuerpo JSON.
- En checkout enviar una `Idempotency-Key` UUID y reutilizarla si se reintenta la misma confirmación; generar una nueva para una compra distinta.
- No registrar JWT, contraseñas, OTP, números completos de tarjeta ni cuerpos sensibles en Logcat.

### Manejo común de errores

- Error de red: mensaje “No se pudo conectar con el servidor” y acción Reintentar.
- HTTP 400/422: mostrar el mensaje funcional devuelto por la API.
- HTTP 401: borrar sesión, conservar navegación pendiente y llevar al login.
- HTTP 403: mostrar acceso no permitido sin reintentos automáticos.
- HTTP 404: mostrar recurso no encontrado.
- HTTP 409: informar conflicto; en checkout consultar si la orden ya fue creada con la clave idempotente.
- HTTP 429: respetar el límite, especialmente para OTP.
- HTTP 5xx: mostrar error temporal y permitir reintento manual.
- Decodificar tanto `{ "message": ... }` como `{ "error": ... }` y cuerpos de texto plano.

## 8. Contratos actuales del backend

Los nombres siguientes se basan en el código actual. Crear DTOs remotos separados de los modelos de dominio para poder tolerar variaciones de nombres.

### 8.1 Autenticación y cuenta

#### Login

`POST /api/login`

```json
{
  "usuario": "nombre_usuario",
  "contrasena": "clave"
}
```

Respuesta actual:

```json
{
  "success": true,
  "user": {
    "usuarioId": 1,
    "usuario": "cliente",
    "nombre": "Nombre",
    "cedula": "0000000000",
    "correo": "cliente@correo.com",
    "telefono": "0000000000",
    "id_rol": 2
  },
  "token": "jwt",
  "access": "jwt"
}
```

Aceptar `token` o `access`, pero guardar una sola copia segura. El rol cliente es `id_rol = 2` o claim `role = CLIENTE`.

#### Registro con OTP

1. `POST /api/otp` con `accion=enviar`, `correo` y `txId` opcional.
2. Mostrar pantalla para código de seis dígitos.
3. `POST /api/otp` con `accion=validar`, `correo`, `codigo` y `txId`.
4. `POST /api/usuarios/crear` con:

```json
{
  "nombre": "Nombre completo",
  "usuario": "usuario",
  "correo": "correo@dominio.com",
  "contrasena": "clave",
  "cedula": "0000000000",
  "telefono": "0000000000"
}
```

Validar contraseñas iguales, mínimo ocho caracteres, correo válido y cédula/teléfono de diez dígitos, replicando el frontend actual. No enviar los campos de UI `repetir` ni códigos OTP dentro del DTO de usuario.

#### Recuperación

Usar `POST /api/usuarios/recuperar-password` con `{ "correo": "..." }`. La respuesta debe tratarse como genérica para no revelar si el correo existe.

#### Perfil

- `GET /api/usuarios/me` -> actualmente envuelve los datos en `{ "data": {...} }`.
- `PUT /api/usuarios/cliente/{id}` existe para edición del cliente; revisar `ClienteUpdateRequest` antes de construir la pantalla editable.
- `POST /api/seguridad/cambiar-password` con `{ "actual": "...", "nueva": "..." }`.

### 8.2 Catálogo

- `GET /api/productos?page=0&size=50` -> actualmente devuelve un array, no `PageResponse`.
- `GET /api/productos/{id}` -> detalle como objeto JSON flexible.
- `GET /api/categorias` -> array de categorías.
- `GET /api/productos/por-categoria?categoriaId={id}`.
- `POST /api/productos/buscar` -> búsqueda avanzada opcional; la primera versión puede filtrar localmente por nombre como hace React.
- `GET /api/galeria_v2/producto/{productoId}?scope=galeria`.
- `GET /api/galeria_v2/img/{galeriaId}` -> contenido binario de imagen.

Campos confirmados del resumen de producto:

```text
producto_id, nombre, preciounitario, enlace, fecha, stock,
marca_id, gama_id, iva_id, costo, habilitado, galeria_id
```

El detalle puede contener nombres heredados diferentes. Encapsular la compatibilidad en el mapper remoto, nunca en composables. Como mínimo tolerar:

- ID: `producto_id`, `productoId`, `id_producto`, `id`.
- Precio: `preciounitario`, `precioUnitario`, `precio`, `costo`.
- Categoría: `categoria`, `categoria_nombre`, `nombre_categoria`.
- Imagen: `imagenId`, `imagen_id`, `portadaId`, `portada_id`, `galeriaId`, `galeria_id`.

No mostrar productos con `habilitado = false`. Mostrar stock agotado y desactivar “Añadir al carrito” cuando `stock <= 0`.

### 8.3 Carrito

Todas las rutas requieren JWT.

- `GET /api/carrito/{usuarioId}` -> crea o devuelve el carrito activo.
- `GET /api/carrito/{carritoId}/detalle` -> `PageResponse<CartLine>`.
- `POST /api/carrito/{carritoId}/agregar` con `{ "productoId": 1, "cantidad": 1 }` -> 204.
- `PUT /api/carrito/{carritoId}/actualizar/{productoId}` con `{ "cantidad": 2 }` -> 204.
- `DELETE /api/carrito/{carritoId}/quitar/{productoId}` -> 204.

Modelos principales:

```text
Cart: carritoId, usuarioId, total, habilitado
CartLine: carritoId, productoId, cantidad, precioUnitario
PageResponse<T>: content, page, size, totalElements, totalPages
```

Para pintar cada línea se debe unir `CartLine` con el catálogo o consultar `GET /api/productos/{productoId}`. No calcular el precio unitario usando un valor antiguo del caché si el servidor devuelve uno en la línea.

### 8.4 Direcciones

- `GET /api/usuarios/{usuarioId}/direcciones?view=full`.
- `POST /api/usuarios/{usuarioId}/direcciones`.
- `PUT /api/usuarios/{usuarioId}/direcciones/{direccionId}`.
- `DELETE /api/usuarios/{usuarioId}/direcciones/{direccionId}`.
- Consultar además `/api/provincias` y `/api/ciudades` según los contratos existentes al crear el formulario.

Campos de dirección:

```text
direccionId, usuarioId, calle, referencia, ciudadId,
ciudadNombre, provinciaNombre, habilitado
```

### 8.5 Métodos de pago

- `GET /api/metodopago/usuario/{usuarioId}` -> `PageResponse<PaymentMethod>`.
- `GET /api/metodopago/tipos`.
- `GET /api/metodopago/{metodopagoId}`.
- `POST /api/metodopago` con `numeroTarjeta`, `fechaExpiracion` en ISO `YYYY-MM-DD` y `tipoId`.
- `PUT /api/metodopago/{id}`.
- `DELETE /api/metodopago/{id}` para inactivar.
- `POST /api/metodopago/{id}/reactivar`.

Respuesta actual de método de pago:

```text
metodopagoId, numeroMascara, fechaExpiracion, habilitado, tipoId, tipoNombre
```

Nunca persistir el número completo de tarjeta en Room, DataStore o logs. Después de enviarlo, conservar únicamente la máscara devuelta por el servidor. No pedir ni almacenar CVV porque el contrato actual no lo usa. La interfaz debe aclarar que se trata del mecanismo académico actual y no de una pasarela PCI real.

### 8.6 Checkout, pedidos y facturas

El frontend React actual deja el botón de pago deshabilitado, pero el microservicio de pedidos sí implementa checkout. La app móvil puede integrarlo y debe manejar con cuidado reintentos y respuestas parciales.

#### Checkout

`POST /api/ordenes/checkout`

Cabecera recomendada:

```text
Idempotency-Key: UUID de esta intención de compra
```

Cuerpo:

```json
{
  "direccionId": 1,
  "metodopagoId": 1
}
```

Respuesta 201 con:

```text
ordenId, usuarioId, direccionId, metodopagoId, subtotal, total, fecha
```

No reintentar automáticamente un POST de checkout con una clave nueva. Si se pierde la respuesta, repetir con la misma clave y luego refrescar el historial.

El backend intenta generar la factura después de crear la orden. Existe un caso explícito en el que la orden queda creada pero la facturación falla; ante un error del checkout, consultar el historial antes de afirmar que no hubo compra.

#### Pedidos

- `GET /api/ordenes/usuario/{usuarioId}?page=0&size=20`.
- `GET /api/ordenes/{ordenId}`.
- `GET /api/ordenes/{ordenId}/detalle?page=0&size=50`.

El modelo actual de orden **no contiene un campo de estado**. Hasta que el backend lo añada, mostrar fecha, total e identificador y usar un texto neutral como “Orden registrada”; no inventar estados “En camino”, “Entregado” o similares.

#### Facturas

- `GET /api/facturas?usuarioId={usuarioId}`.
- `GET /api/facturas/{facturaId}`.
- `GET /api/facturas/{facturaId}/detalle`.

El microservicio distribuido actual no expone descarga PDF. No implementar `/pdf` en móvil sin verificar que la ruta haya sido añadida al servicio y al Gateway.

## 9. Pantallas y comportamiento

### 9.1 Arranque

- Mostrar splash breve con marca TiendaTech.
- Restaurar sesión segura.
- Si no hay token, permitir navegar por el catálogo público.
- Si el token está vencido, borrarlo y continuar como invitado.
- Mantener la ruta deseada para regresar después del login.

### 9.2 Inicio/catálogo

- Mensaje de marca equivalente a “Todo para construir algo increíble”.
- Búsqueda por nombre.
- Chips horizontales de categorías, incluyendo “Todos”.
- Cuadrícula adaptativa o lista de tarjetas con imagen, categoría, nombre, descripción corta, precio y stock.
- Pull to refresh.
- Estados separados: carga inicial, contenido, vacío, error y contenido cacheado sin conexión.
- Tocar una tarjeta abre el detalle.
- Acción visible para abrir el escáner.

### 9.3 Detalle de producto

- Galería deslizable y placeholder local si falla una imagen.
- Categoría, nombre, descripción, precio y stock.
- Selector de cantidad entre 1 y 99, limitado además por stock cuando sea confiable.
- “Añadir al carrito”. Si no hay sesión, abrir login y volver al producto después.
- Confirmación breve y acción “Ver carrito”.

### 9.4 Carrito

- Requiere sesión.
- Mostrar imagen, nombre, precio unitario, cantidad y subtotal por línea.
- Controles aumentar/disminuir, edición válida de cantidad y eliminar.
- Deshabilitar temporalmente solo la línea que se está actualizando.
- Resumen con unidades, subtotal y total.
- Carrito vacío con acción para volver al catálogo.
- Acción “Continuar al pago”.

### 9.5 Checkout

- Requiere carrito no vacío.
- Seleccionar una dirección habilitada.
- Permitir crear una dirección si no existe.
- Seleccionar un método de pago habilitado.
- Permitir crear un método si no existe.
- Mostrar unidades, subtotal y total.
- Confirmación final explícita antes de llamar al endpoint.
- Bloquear doble toque y usar idempotencia.
- Al éxito, limpiar el estado local del carrito, mostrar número de orden y ofrecer “Ver pedido” y “Seguir comprando”.
- Ante respuesta ambigua, verificar historial antes de permitir otro intento.

### 9.6 Pedidos

- Lista paginada del usuario actual.
- Mostrar número, fecha y total.
- Detalle con líneas, cantidades, impuestos y totales.
- Enlazar a la factura cuando pueda relacionarse por `ordenId`.
- No mostrar estados de envío ficticios.

### 9.7 Cuenta

- Perfil: nombre, usuario, correo, teléfono y cédula.
- Direcciones: listar, crear, editar y eliminar.
- Métodos de pago: listar máscaras, crear, inactivar y reactivar.
- Cambio de contraseña.
- Selector de tema.
- Cerrar sesión, borrando token, datos de usuario, claves de checkout pendientes y caché privada.

### 9.8 Registro y recuperación

- Replicar el flujo OTP de dos etapas del frontend React.
- Permitir corregir datos antes de crear la cuenta.
- Evitar doble envío del formulario.
- No conservar contraseña u OTP al salir del flujo.
- Mostrar mensajes genéricos en recuperación de contraseña.

## 10. Cámara y códigos de barras

Usar CameraX y ML Kit Barcode Scanning. Aceptar inicialmente EAN-8, EAN-13, UPC-A, UPC-E, Code 128 y QR.

Flujo:

1. El usuario toca Escanear.
2. Explicar por qué se necesita cámara.
3. Solicitar permiso.
4. Analizar frames hasta obtener un valor válido.
5. Pausar el análisis para evitar lecturas duplicadas.
6. Buscar el producto y abrir su detalle o mostrar “No encontrado”.
7. Permitir reintentar o escribir el código manualmente.

### Bloqueo actual

El backend de productos no expone un campo ni endpoint confirmado de código de barras. El campo `enlace` **no debe asumirse** como código. La IA puede construir la cámara, el analizador, la abstracción `ProductLookupByBarcode` y una implementación falsa para pruebas, pero no debe afirmar que el escaneo está integrado hasta que exista un contrato backend, por ejemplo:

```text
GET /api/productos/por-codigo/{codigo}
```

La adición de ese contrato requiere una tarea separada de backend.

## 11. Notificaciones push

Preparar Firebase Cloud Messaging con:

- Canal Android `pedidos`.
- Manejo de token FCM y renovación.
- Notificación que abra el detalle del pedido mediante deep link.
- Permiso en Android 13+.
- Pruebas del mapeo de payload sin depender de Firebase real.

### Bloqueo actual

No existe un `notification-service`, endpoint de registro de dispositivo ni evento/estado de pedido suficiente en el backend actual. No subir un `google-services.json` inventado y no simular que las notificaciones son reales.

Para completar la función se necesitará, como mínimo:

- Configuración Firebase real fuera del control de versiones cuando contenga datos sensibles.
- Endpoint autenticado para registrar/revocar el token FCM por usuario y dispositivo.
- Estado de pedido persistido.
- Evento de cambio de estado que dispare la notificación.
- Payload con `ordenId`, tipo de evento, título y mensaje.

Mientras tanto se puede implementar la UI del canal, el receptor y una demostración local mediante notificación de prueba claramente etiquetada.

## 12. Caché y modo sin conexión

Room debe almacenar solamente datos útiles y no sensibles:

- Productos y categorías.
- Metadatos de imágenes, no necesariamente todos los binarios.
- Marca de última actualización.
- Opcionalmente pedidos ya consultados, separados por usuario.

Reglas:

- Mostrar catálogo cacheado inmediatamente y refrescar desde red.
- Indicar cuando el contenido puede estar desactualizado.
- No permitir checkout sin conexión.
- No poner en cola cambios de carrito en la primera versión; mostrar que requieren conexión.
- No cachear números completos de tarjeta, contraseña, OTP ni JWT en Room.
- Borrar los datos privados asociados al usuario al cerrar sesión.

## 13. Dependencias esperadas

Añadirlas mediante el catálogo `gradle/libs.versions.toml` y evitar versiones literales dispersas:

- Navigation Compose.
- Lifecycle ViewModel Compose y lifecycle runtime Compose.
- Kotlin Coroutines Android y test.
- Hilt Android, compilador y Hilt Navigation Compose.
- Retrofit, OkHttp logging controlado y Kotlin serialization converter.
- Kotlin serialization plugin/runtime.
- Room runtime, KTX, compiler con KSP y Room testing.
- DataStore Preferences.
- Coil Compose.
- CameraX core, camera2, lifecycle y view/Compose según API estable.
- ML Kit Barcode Scanning.
- Firebase BOM y Firebase Messaging cuando haya configuración real.
- JUnit 5 para pruebas unitarias de ViewModels, MockK o Mockito-Kotlin y Turbine.
- Compose UI Test y AndroidX Test para instrumentadas.

No añadir todas las dependencias en un único cambio sin usar. Incorporarlas por etapa y mantener el proyecto compilando.

## 14. Estrategia de pruebas

Objetivo de cobertura: al menos 70 % en lógica móvil medible.

### Unitarias

- Mappers de DTO remoto a dominio, incluidos nombres alternativos de producto.
- Validaciones de login, registro, OTP, dirección y método de pago.
- ViewModels: carga, éxito, vacío, error y reintento.
- Cálculos de totales del carrito solo como presentación; contrastar con valores del servidor.
- Manejo de 401 y cierre de sesión.
- Idempotencia del checkout y prevención de doble envío.
- Decodificación de errores JSON y texto plano.

### Datos/integración local

- DAO y migraciones Room.
- Repositorios con servidor HTTP falso.
- Interceptor Bearer sin exponer token.
- Política de caché y separación por usuario.

### Instrumentadas

Como mínimo un flujo E2E estable:

1. Abrir catálogo con datos controlados.
2. Entrar al detalle.
3. Iniciar sesión o usar sesión de prueba.
4. Agregar al carrito.
5. Cambiar cantidad.
6. Llegar al resumen de checkout.

Agregar pruebas de navegación de login, estado vacío, error y tema oscuro. Cámara y FCM deben envolverse en interfaces para poder probar la UI sin hardware ni servicios reales.

## 15. Observabilidad y privacidad

- Crear un `X-Request-Id` UUID por petición o conservar el que devuelva el servidor.
- Preparar medición de latencia extremo a extremo sin registrar datos personales.
- En debug, el interceptor HTTP puede registrar método, ruta, código y duración; nunca cuerpos de autenticación o pago.
- En release, desactivar logging HTTP detallado.
- No incluir correo, cédula, teléfono, dirección, JWT, OTP o tarjeta en eventos analíticos.
- Preparar puntos de instrumentación para OpenTelemetry si se incorpora una librería móvil compatible y estable.

## 16. CI/CD y artefactos

El pipeline debe ejecutar desde `Apps/mobile/`:

```text
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Cuando exista emulador en CI, añadir pruebas instrumentadas. Publicar el APK resultante desde:

```text
Apps/mobile/app/build/outputs/apk/debug/*.apk
```

El APK académico puede estar firmado con clave debug según la guía. No versionar keystores de producción ni contraseñas.

Antes de dar una etapa por terminada, exigir:

- Sin errores de compilación.
- Lint sin errores nuevos.
- Pruebas verdes.
- Sin secretos en Git.
- Captura o evidencia de la pantalla implementada.
- Actualización breve del README móvil.

## 17. Orden de implementación para la IA de Android Studio

La IA debe trabajar en entregas pequeñas. Al finalizar cada fase debe compilar, ejecutar pruebas y resumir archivos modificados, decisiones y pendientes. No debe comenzar la siguiente fase si la anterior no compila.

### Fase 0: saneamiento del proyecto

- Renombrar app y paquete.
- Configurar minSdk 26, Java 17, BuildConfig y permisos iniciales.
- Crear tema TiendaTech claro/oscuro sin colores dinámicos por defecto.
- Agregar navegación y pantalla base.
- Mantener `assembleDebug` y pruebas verdes.

### Fase 1: núcleo técnico

- Hilt, cliente HTTP, serialización, manejo de errores y configuración por entorno.
- Almacenamiento seguro de sesión.
- Room y DataStore.
- Componentes reutilizables de carga, error, vacío, botones y tarjetas.
- Pruebas del núcleo.

### Fase 2: autenticación

- Login, restauración de sesión, logout y rutas protegidas.
- Registro OTP y recuperación.
- Validaciones y pruebas.

### Fase 3: catálogo

- Productos, categorías, búsqueda, galería, caché y pull to refresh.
- Detalle y placeholders.
- Pruebas de mappers y ViewModels.

### Fase 4: carrito

- Obtener/crear carrito, líneas, agregar, actualizar y eliminar.
- Contador global y navegación protegida.
- Pruebas de errores y concurrencia de UI.

### Fase 5: cuenta y checkout

- Perfil, CRUD de direcciones y métodos de pago.
- Checkout con idempotencia.
- Pantalla de éxito y verificación posterior a respuestas ambiguas.

### Fase 6: pedidos y facturas

- Historial paginado, detalle de orden y factura.
- No inventar estados ni PDF.

### Fase 7: cámara

- Permisos, CameraX, ML Kit y UI completa.
- Mantener lookup desacoplado hasta que exista endpoint de código de barras.

### Fase 8: notificaciones

- Canal, permiso, receptor y deep link.
- Integrar FCM real únicamente después de disponer de configuración y endpoint backend.

### Fase 9: calidad y entrega

- Cobertura, instrumentadas, accesibilidad, modo oscuro, rotación, proceso muerto y mala conectividad.
- Integración CI y APK.
- Documentar ADR de elección móvil con criterios cuantitativos.

## 18. Criterios de aceptación globales

La primera versión está lista cuando:

- Compila desde cero con el wrapper incluido.
- Funciona en API 26 o superior.
- Consume únicamente el Gateway configurable.
- Permite navegar el catálogo sin sesión.
- Protege carrito, checkout, pedidos y cuenta.
- Permite a un CLIENTE iniciar sesión y rechaza roles no móviles.
- Permite completar un checkout real con dirección y método de pago existentes.
- Evita duplicar compras ante doble toque o reintento.
- Muestra historial y detalle reales sin inventar estado.
- Usa caché de catálogo e informa falta de conexión.
- Almacena el JWT cifrado y no filtra datos sensibles en logs o backups.
- Tiene tema claro/oscuro y accesibilidad básica.
- Cuenta con pruebas unitarias suficientes para el objetivo de cobertura y al menos una prueba instrumentada E2E.
- Produce un APK mediante CI.
- Cámara y notificaciones están completas o aparecen documentadas honestamente como bloqueadas por los contratos backend pendientes.

## 19. Instrucción operativa para la IA

Usar este texto al iniciar cada sesión de implementación:

> Lee completamente `docs/alcance-aplicacion-movil.md`. Implementa únicamente la fase que te indique dentro de `Apps/mobile/`. Antes de editar, inspecciona el código actual y los contratos backend citados. No inventes endpoints, campos, credenciales ni respuestas. No modifiques web, backend, Docker o documentación fuera del alcance solicitado. Conserva los cambios existentes del usuario. Al terminar, ejecuta las tareas Gradle pertinentes, corrige los fallos causados por tus cambios y entrega un resumen de archivos modificados, pruebas ejecutadas, decisiones y bloqueos reales.

## 20. Pendientes que requieren trabajo fuera de Android

No deben resolverse silenciosamente desde la app:

1. Añadir un identificador de código de barras a productos y un endpoint de búsqueda por código.
2. Añadir estado y transiciones del pedido.
3. Crear infraestructura de eventos/notificaciones y registro de tokens FCM.
4. Confirmar configuración Firebase del proyecto.
5. Decidir si se expondrá PDF de factura en el microservicio distribuido.
6. Confirmar URL/HTTPS del Gateway para una instalación fuera de la red local.
7. Revisar el caso en que la orden se confirma pero falla la creación de factura.

Estas limitaciones deben aparecer en la documentación y demostración; no se deben ocultar con datos falsos de producción.
