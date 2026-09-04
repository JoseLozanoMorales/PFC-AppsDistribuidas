# Paso 9 - Evidencia rojo/verde del pipeline CI/CD

> **Estado: secuencia ya ejecutada.** Por el historial de git (`git log`), la secuencia
> de este documento ya corrió: PR #81 (rama `ci/demo-rojo-verde`, commit `f58869b`,
> merge `d183382`) contiene el fallo intencional; PR #82 (misma rama reabierta,
> commit `e494b1a`, merge `21db029`) revierte la aserción, y PR #83 contiene
> la corrección final que devuelve el pipeline a verde.
> - PR rojo: https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/33554959074
> - PR verde: https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/33557738789
>
> Ambos enlaces apuntan a ejecuciones concretas de `CI-CD quality gate` disparadas
> por `pull_request`, verificadas mediante la API de GitHub: la primera concluyó
> `failure` sobre `f58869b` y la segunda `success` sobre `bd027f0`.
>
> **Efecto colateral encontrado y corregido:** el comando de PowerShell de reversión
> (`Set-Content -Encoding utf8`) dejó un BOM UTF-8 al inicio de `PaginacionTest.java`,
> lo que rompía la compilación de *todo* el módulo de test de pedidos-service
> (no solo el test tocado). Ya se corrigió el archivo (se quitó el BOM, mismo contenido)
> y se actualizaron los comandos de este documento para no repetirlo.

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
$f = "services/pedidos-service/src/test/java/com/tiendatech/pedidos/domain/PaginacionTest.java"
$text = (Get-Content -Raw $f) -replace 'assertThat\(paginacion\.size\(\)\)\.isEqualTo\(20\);', 'assertThat(paginacion.size()).isEqualTo(21);'
[System.IO.File]::WriteAllText($f, $text, (New-Object System.Text.UTF8Encoding($false)))
```

> **No uses `Set-Content -Encoding utf8`**: en PowerShell 5.1 eso escribe un BOM UTF-8
> al inicio del archivo. `javac` en el contenedor `maven:3.9-eclipse-temurin-21` no lo
> tolera y rompe la compilación de *todo* el módulo de test con
> `illegal character: '﻿'` — no solo el test que tocaste. `[System.IO.File]::WriteAllText`
> con `UTF8Encoding($false)` escribe UTF-8 sin BOM y evita el problema.

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

**Enlace a la ejecución en rojo:**
https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/33554959074

## Paso 2: corregir y volver a verde

Revertir la línea 18 a su valor original. Comando exacto (mismo mecanismo, a la inversa):

```powershell
$f = "services/pedidos-service/src/test/java/com/tiendatech/pedidos/domain/PaginacionTest.java"
$text = (Get-Content -Raw $f) -replace 'assertThat\(paginacion\.size\(\)\)\.isEqualTo\(21\);', 'assertThat(paginacion.size()).isEqualTo(20);'
[System.IO.File]::WriteAllText($f, $text, (New-Object System.Text.UTF8Encoding($false)))

git add services/pedidos-service/src/test/java/com/tiendatech/pedidos/domain/PaginacionTest.java
git commit -m "ci: revertir fallo intencional, vuelve a verde"
git push
```

El mismo PR debe volver a mostrar el job `test-java` en verde para todos los servicios
de la matriz.

**Enlace a la ejecución en verde:**
https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/33557738789

Con ambos enlaces pegados, cerrar/mergear el PR (o descartar la rama `ci/demo-rojo-verde`
si no se quiere mergear) y este punto del Paso 9 queda cerrado.
