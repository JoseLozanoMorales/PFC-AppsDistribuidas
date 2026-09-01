# Paso 9 - Evidencia rojo/verde del pipeline CI/CD

Este documento deja preparado el cambio mínimo para que `.github/workflows/ci-cd.yml`
falle de forma limpia y evidente, y la corrección exacta para devolverlo a verde.
Lo ejecuta el Responsable de Calidad (no se automatiza aquí porque implica push a GitHub).

## Por qué este cambio y no otro

Se necesita una falla **evidente y aislada**: un solo test de lógica de negocio pura,
sin dependencias externas (sin BD, sin red, sin mocks), que falle por una aserción
incorrecta y no por un error de compilación ambiguo. `Paginacion` es el clamp de
paginación que usan los controladores antes de llegar a los servicios de aplicación
de pedidos-service — es exactamente la "lógica de negocio" que pide el Paso 9, y el
test es 100% determinista.

## Paso 1: provocar el fallo (rojo)

Archivo: `services/pedidos-service/src/test/java/com/tiendatech/pedidos/domain/PaginacionTest.java`,
línea 18.

Cambiar:

```java
        assertThat(paginacion.size()).isEqualTo(20);
```

por:

```java
        assertThat(paginacion.size()).isEqualTo(21);
```

Comando exacto para editar la línea sin abrir un editor (PowerShell, desde la raíz del repo):

```powershell
(Get-Content services/pedidos-service/src/test/java/com/tiendatech/pedidos/domain/PaginacionTest.java) `
  -replace 'assertThat\(paginacion\.size\(\)\)\.isEqualTo\(20\);', 'assertThat(paginacion.size()).isEqualTo(21);' |
  Set-Content -Encoding utf8 services/pedidos-service/src/test/java/com/tiendatech/pedidos/domain/PaginacionTest.java
```

Commitear y subir en una rama de feature (para que dispare el trigger `pull_request`
y no ensucie `main` directamente):

```powershell
git checkout -b ci/demo-rojo-verde
git add services/pedidos-service/src/test/java/com/tiendatech/pedidos/domain/PaginacionTest.java
git commit -m "ci: forzar fallo intencional para evidencia rojo/verde"
git push -u origin ci/demo-rojo-verde
```

Abrir el Pull Request contra `main`. El job `test-java` (matriz `pedidos-service`)
debe fallar con un mensaje evidente tipo:

```
de_conPageYSizeAusentes_usaDefaults0y20  FAILED
  org.opentest4j.AssertionFailedError: expected: 21 but was: 20
```

Los demás jobs de la matriz (`inventario-service`, `productos-service`, etc.) y los
demás trabajos (`test-python`, `lint`, `integration-test`, `build`) no se ven afectados
por este cambio: solo falla la celda `test-java (pedidos-service)`.

**Enlace a la ejecución en rojo:** _pegar aquí el link al run de GitHub Actions_

## Paso 2: corregir y volver a verde

Revertir la línea 18 a su valor original. Comando exacto (mismo mecanismo, a la inversa):

```powershell
(Get-Content services/pedidos-service/src/test/java/com/tiendatech/pedidos/domain/PaginacionTest.java) `
  -replace 'assertThat\(paginacion\.size\(\)\)\.isEqualTo\(21\);', 'assertThat(paginacion.size()).isEqualTo(20);' |
  Set-Content -Encoding utf8 services/pedidos-service/src/test/java/com/tiendatech/pedidos/domain/PaginacionTest.java

git add services/pedidos-service/src/test/java/com/tiendatech/pedidos/domain/PaginacionTest.java
git commit -m "ci: revertir fallo intencional, vuelve a verde"
git push
```

El mismo PR debe volver a mostrar el job `test-java` en verde para todos los servicios
de la matriz.

**Enlace a la ejecución en verde:** _pegar aquí el link al run de GitHub Actions_

Con ambos enlaces pegados, cerrar/mergear el PR (o descartar la rama `ci/demo-rojo-verde`
si no se quiere mergear) y este punto del Paso 9 queda cerrado.
