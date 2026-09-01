# Documento acumulativo — cierre del Paso 13

La versión de cierre es `PFC4.tex` y su salida `PFC4.pdf`. El contenido de cierre se integró en los nombres originales. El informe usa evidencia del commit `6cfe0e8bac8d142b7986a4a3265721f404e84156`, consultado el 1 de septiembre de 2026; no supone que el árbol local coincida con él.

## Compilación exacta

Desde la raíz del repositorio, con TeX Live y Biber instalados:

```powershell
cd docs/entrega4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
biber PFC4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
```

No usar BibTeX para esta versión. La bibliografía es `../entrega3/referenciasPFC.bib`, compartida con las entregas anteriores; se conservaron las entradas históricas no sustituidas. Se requieren `UteqLogo.png`, las imágenes relativas en `img/` y las figuras de `cierre/`.

Alternativa con la imagen TeX Live ya disponible, desde la raíz del repositorio en PowerShell:

```powershell
docker run --rm --network none --mount "type=bind,source=$($PWD.Path),target=/work" -w /work/docs/entrega4 texlive/texlive:latest sh -c "pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex && biber PFC4 && pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex && pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex"
```

La compilación no inicia TiendaTech ni ejecuta experimentos. El cierre se compiló con TeX Live 2026 y Biber; la etiqueta Docker `latest` es mutable, por lo que para reproducibilidad estricta debe fijarse el digest de la imagen utilizada.

## Figuras reproducibles

Las figuras PNG ya están incluidas. Para regenerarlas se requiere Python con Matplotlib (validado con 3.11.1):

```powershell
python docs/entrega4/cierre/generar_figuras.py
```

El script solo lee los CSV incluidos. No genera mediciones, no cambia sus valores y no consulta servicios. Los diagramas C4 se generan directamente desde TikZ en el archivo principal.

## Evidencia y límites

- `cierre/experimento_crudo.csv` y `experimento_resumen.csv`: copias del Paso 8 en el commit de corte.
- `cierre/tiempos_resumen.csv`: copia de `resultados/tiempos_resumen.csv` del mismo commit.
- `cierre/issues-corte.json`: estado y última respuesta de los issues 16–78; 63 registros, 50 cerrados y 13 abiertos. Tres abiertos son duplicados aparentes.
- `cierre/doi-verificados.json`: metadatos consultados en Crossref de las quince fuentes académicas seleccionadas. Se contrastaron título, autores y DOI; las normas y el código ético se citan mediante URL institucional.
- Planes SQL: `docs/evidencias/resultados-planes-e4/comparativa-planes.csv` en el commit de corte.
- Tolerancia: `docs/evidencias/resultados-tolerancia-e4/mediciones.csv` y `tiempo-reintegracion.csv` en el mismo commit.
- Calidad: `docs/evidencias/cobertura/` y `docs/experimentos/resultados/iso25010/complejidad/summary.csv` del mismo commit.

Las cifras de cobertura son del alcance instrumentado, no de la totalidad de cada servicio. El banco 2PC/Saga es local, usa SQLite y un bloqueo global, y no completa el experimento distribuido solicitado. Su evaluación de compatibilidad no llama al asistente real ni compara RAG. El informe no acredita una hora de disponibilidad, carga oficial final, firmas personales ni similitud inferior al 15 %.

Las conclusiones individuales proceden de los textos anteriores y se actualizaron; cada autor debe revisarlas antes de entregarlas como declaración personal. La memoria registra los pendientes, no los convierte en funcionalidades terminadas.

## Estado de publicación

La migración no añade archivos a Git. El logo y `cierre/` siguen sin rastrear en este corte local; deben incorporarse si se desea compilar desde un clon. Los v2 se mantienen únicamente como respaldo temporal y no son dependencias del documento oficial.
