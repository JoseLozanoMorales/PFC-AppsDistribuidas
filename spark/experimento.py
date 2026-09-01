"""Orquesta las repeticiones del Paso 6 y dispara el análisis final.

Matriz: etapas (T1..T5) x ejecutores (1/2/4, sobre el standalone Spark local
spark://e4-spark-master:7077) x repeticiones. Por cada combinación etapa x
ejecutores se corren 1 ejecución de calentamiento (descartada) + N
repeticiones útiles (por defecto 5, según la rúbrica). El CSV crudo marca
explícitamente cuál fila es el calentamiento y cuáles son las útiles.

Este script NO tiene dependencias de terceros a propósito (solo librería
estándar): así puede correr con cualquier Python del host sin instalar nada
y sin depender de un venv de este ni de otro proyecto. Todo el trabajo con
dependencias de terceros —pipeline.py, baseline.py, y el análisis
estadístico/figuras final— corre DENTRO del contenedor de Spark
(tiendatech-spark:3.5.5), vía `docker run` construido aquí mismo con
subprocess (nunca a través de PowerShell ni Git Bash, para evitar el
mangling de paths de MSYS y el wrapping de stderr nativo de PowerShell 5.1).
Ver la política de entornos en spark/PLAN-PASO6.md.
"""

from __future__ import annotations

import argparse
import csv
import ctypes
import json
import subprocess
from pathlib import Path


class _MEMORYSTATUSEX(ctypes.Structure):
    _fields_ = [
        ("dwLength", ctypes.c_ulong),
        ("dwMemoryLoad", ctypes.c_ulong),
        ("ullTotalPhys", ctypes.c_ulonglong),
        ("ullAvailPhys", ctypes.c_ulonglong),
        ("ullTotalPageFile", ctypes.c_ulonglong),
        ("ullAvailPageFile", ctypes.c_ulonglong),
        ("ullTotalVirtual", ctypes.c_ulonglong),
        ("ullAvailVirtual", ctypes.c_ulonglong),
        ("sullAvailExtendedVirtual", ctypes.c_ulonglong),
    ]


def free_ram_gb() -> float:
    """RAM libre del host (Windows), vía GlobalMemoryStatusEx (stdlib, sin
    dependencias de terceros). Chequeo SÍNCRONO en el mismo proceso que
    lanza cada corrida: no depende de un monitor externo que intente matar
    el contenedor a tiempo (eso puede perder la carrera si la corrida ya
    terminó cuando llega la orden de matar)."""
    status = _MEMORYSTATUSEX()
    status.dwLength = ctypes.sizeof(_MEMORYSTATUSEX)
    ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status))
    return status.ullAvailPhys / (1024**3)


RAM_MINIMA_GB = 1.0


ETAPAS = ["t1", "t2", "t3", "t4", "t5"]
DEFAULT_JDBC_JAR = str(
    Path.home() / ".m2/repository/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar"
)
CSV_FIELDNAMES = [
    "motor",
    "etapa",
    "master",
    "ejecutores",
    "orden_ejecucion",
    "es_calentamiento",
    "repeticion_util",
    "repeticion",
    "duracion_segundos",
    "tiempo_jdbc_segundos",
    "tiempo_computo_segundos",
    "cpu_segundos",
    "rss_inicial_mb",
    "rss_final_mb",
    "ordenes_fuente",
    "detalles_fuente",
    "usuarios_fuente",
    "filas_resultado_etapa",
    "dataset_max_order_id",
    "python",
    "spark",
    "pandas",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--etapas", nargs="+", default=list(ETAPAS), choices=ETAPAS)
    parser.add_argument("--ejecutores", type=int, nargs="+", default=[1, 2, 4])
    parser.add_argument(
        "--repeticiones-utiles",
        type=int,
        default=5,
        help="Mediciones que cuentan por combinación etapa x ejecutores (además de 1 calentamiento descartado).",
    )
    parser.add_argument("--output", type=Path, default=Path("spark/out/experimento"))
    parser.add_argument("--jdbc-jar", default=DEFAULT_JDBC_JAR)
    parser.add_argument("--incluir-pandas", action="store_true")
    parser.add_argument(
        "--solo-pandas",
        action="store_true",
        help="Saltar las corridas de PySpark y correr solo el baseline pandas por etapa "
        "(implica --incluir-pandas). Útil para completar el baseline sin repetir corridas ya hechas.",
    )
    parser.add_argument(
        "--sin-analisis",
        action="store_true",
        help="No correr spark/analizar_experimento.py al final (solo generar mediciones.csv).",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Solo imprime el plan (número de ejecuciones) y sale, sin correr nada.",
    )
    parser.add_argument(
        "--append",
        action="store_true",
        help="Agregar filas a un mediciones.csv existente (sin reescribir el encabezado) en vez "
        "de sobreescribirlo. Usar para completar un lote interrumpido sin perder lo ya medido.",
    )
    parser.add_argument(
        "--particionar-lectura",
        action="store_true",
        help="Pasar --particionar-lectura a pipeline.py para medir JDBC particionado.",
    )
    parser.add_argument(
        "--particiones-lectura",
        type=int,
        default=4,
        help="numPartitions que se pasa a pipeline.py cuando --particionar-lectura esta activo.",
    )
    return parser.parse_args()


class DetencionPorRAM(Exception):
    """Se lanza cuando la RAM libre del host cae debajo de RAM_MINIMA_GB, para
    frenar el bucle de corridas de forma explícita e inmediata (no depende de
    que un `docker kill` externo alcance a interrumpir la corrida a tiempo)."""


def run(command: list[str]) -> None:
    subprocess.run(command, check=True)


def build_standalone_command(
    root: Path,
    jdbc_jar: Path,
    etapa: str,
    ejecutores: int,
    repeticion: int,
    output_dir: Path,
    *,
    habilitar_ui: bool = False,
    pausa_ui_segundos: int = 180,
    extra_pipeline_args: list[str] | None = None,
) -> list[str]:
    container_output = "/workspace/" + output_dir.relative_to(root).as_posix()
    command = [
        "docker", "run", "--rm",
        "--name", "e4-pipeline-driver",
        "--hostname", "e4-pipeline-driver",
        "--network", "tiendatech_default",
    ]
    if habilitar_ui:
        command += ["-p", "127.0.0.1:4040:4040"]
    command += [
        "-v", f"{root}:/workspace",
        "-v", f"{jdbc_jar}:/opt/jdbc/postgresql.jar:ro",
        "tiendatech-spark:3.5.5",
        "/opt/spark/bin/spark-submit",
        "--master", "spark://e4-spark-master:7077",
        "--conf", "spark.driver.host=e4-pipeline-driver",
        "--conf", "spark.executor.memory=512m",
        "--conf", "spark.executor.cores=1",
        "--total-executor-cores", str(ejecutores),
        "--jars", "/opt/jdbc/postgresql.jar",
        "/workspace/spark/pipeline.py",
        "--etapa", etapa,
        "--master", "spark://e4-spark-master:7077",
        "--jdbc-url", "jdbc:postgresql://crdb-local:26257/tiendatech?sslmode=disable",
        "--jdbc-jar", "/opt/jdbc/postgresql.jar",
        "--output", container_output,
        "--run", str(repeticion),
        "--overwrite",
    ]
    if habilitar_ui:
        command += ["--habilitar-ui", "--pausa-ui-segundos", str(pausa_ui_segundos)]
    if extra_pipeline_args:
        command += extra_pipeline_args
    return command


def build_pandas_command(root: Path, etapa: str, repeticion: int, output_dir: Path) -> list[str]:
    container_output = "/workspace/" + output_dir.relative_to(root).as_posix()
    return [
        "docker", "run", "--rm",
        "--network", "tiendatech_default",
        "-v", f"{root}:/workspace",
        "tiendatech-spark:3.5.5",
        "python3", "/workspace/spark/baseline.py",
        "--etapa", etapa,
        "--dsn", "postgresql://root@crdb-local:26257/tiendatech?sslmode=disable",
        "--run", str(repeticion),
        "--output", container_output,
        "--metrics", f"{container_output}/metricas.json",
        "--overwrite",
    ]


def build_analysis_command(root: Path, output_dir: Path) -> list[str]:
    container_output = "/workspace/" + output_dir.relative_to(root).as_posix()
    return [
        "docker", "run", "--rm",
        "-v", f"{root}:/workspace",
        "tiendatech-spark:3.5.5",
        "python3", "/workspace/spark/analizar_experimento.py",
        "--input", f"{container_output}/mediciones.csv",
        "--output", container_output,
    ]


def build_plan(args: argparse.Namespace) -> list[dict]:
    total_corridas = args.repeticiones_utiles + 1  # +1 de calentamiento
    plan: list[dict] = []
    for etapa in args.etapas:
        if not args.solo_pandas:
            for ejecutores in args.ejecutores:
                for orden in range(1, total_corridas + 1):
                    plan.append({"motor": "pyspark", "etapa": etapa, "ejecutores": ejecutores, "orden_ejecucion": orden})
        if args.incluir_pandas:
            for orden in range(1, total_corridas + 1):
                plan.append({"motor": "pandas", "etapa": etapa, "ejecutores": None, "orden_ejecucion": orden})
    return plan


def main() -> None:
    args = parse_args()
    if args.solo_pandas:
        args.incluir_pandas = True
    if args.repeticiones_utiles < 2:
        raise ValueError("--repeticiones-utiles debe ser >= 2 para poder calcular desviación/IC en el análisis.")
    root = Path(__file__).resolve().parent.parent
    jdbc_jar = Path(args.jdbc_jar)
    args.output = args.output.resolve()

    plan = build_plan(args)
    if args.dry_run:
        por_motor: dict[str, int] = {}
        for item in plan:
            por_motor[item["motor"]] = por_motor.get(item["motor"], 0) + 1
        print(f"Plan: {len(plan)} ejecuciones totales.")
        for motor, n in por_motor.items():
            print(f"  {motor}: {n} ejecuciones "
                  f"({len(args.etapas)} etapas x "
                  f"{'{} ejecutores x '.format(len(args.ejecutores)) if motor == 'pyspark' else ''}"
                  f"{args.repeticiones_utiles + 1} corridas [1 calentamiento + {args.repeticiones_utiles} útiles])")
        return

    if not jdbc_jar.is_file():
        raise FileNotFoundError(f"No se encontró el driver JDBC en {jdbc_jar}")

    args.output.mkdir(parents=True, exist_ok=True)
    total_corridas = args.repeticiones_utiles + 1
    n_filas = 0

    raw_csv = args.output / "mediciones.csv"
    modo = "a" if (args.append and raw_csv.exists()) else "w"
    escribir_encabezado = modo == "w"

    detenido_por_ram = False
    with raw_csv.open(modo, newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=CSV_FIELDNAMES)
        if escribir_encabezado:
            writer.writeheader()
            stream.flush()

        try:
            for etapa in args.etapas:
                if not args.solo_pandas:
                    for ejecutores in args.ejecutores:
                        for orden in range(1, total_corridas + 1):
                            libre = free_ram_gb()
                            if libre < RAM_MINIMA_GB:
                                raise DetencionPorRAM(
                                    f"RAM libre del host = {libre:.3f} GB (< {RAM_MINIMA_GB} GB) "
                                    f"justo antes de etapa={etapa} ejecutores={ejecutores} orden={orden}."
                                )
                            es_calentamiento = orden == 1
                            repeticion_util = None if es_calentamiento else orden - 1
                            run_dir = (
                                args.output / "pyspark" / etapa / f"e{ejecutores}" / f"corrida{orden:02d}"
                            ).resolve()
                            extra_args = []
                            if args.particionar_lectura:
                                extra_args = [
                                    "--particionar-lectura",
                                    "--particiones-lectura",
                                    str(args.particiones_lectura),
                                ]
                            command = build_standalone_command(
                                root,
                                jdbc_jar,
                                etapa,
                                ejecutores,
                                orden,
                                run_dir,
                                extra_pipeline_args=extra_args,
                            )
                            run(command)
                            row = json.loads((run_dir / "metricas.json").read_text(encoding="utf-8"))
                            row.update({
                                "ejecutores": ejecutores,
                                "orden_ejecucion": orden,
                                "es_calentamiento": es_calentamiento,
                                "repeticion_util": repeticion_util,
                            })
                            writer.writerow(row)
                            stream.flush()
                            n_filas += 1
                            print(
                                f"[{n_filas}] pyspark etapa={etapa} ejecutores={ejecutores} "
                                f"orden={orden} calentamiento={es_calentamiento} "
                                f"duracion={row['duracion_segundos']:.3f}s libre_ram_gb={libre:.2f}"
                            )
                if args.incluir_pandas:
                    for orden in range(1, total_corridas + 1):
                        libre = free_ram_gb()
                        if libre < RAM_MINIMA_GB:
                            raise DetencionPorRAM(
                                f"RAM libre del host = {libre:.3f} GB (< {RAM_MINIMA_GB} GB) "
                                f"justo antes de pandas etapa={etapa} orden={orden}."
                            )
                        es_calentamiento = orden == 1
                        repeticion_util = None if es_calentamiento else orden - 1
                        run_dir = (args.output / "pandas" / etapa / f"corrida{orden:02d}").resolve()
                        command = build_pandas_command(root, etapa, orden, run_dir)
                        run(command)
                        row = json.loads((run_dir / "metricas.json").read_text(encoding="utf-8"))
                        row.update({
                            "ejecutores": None,
                            "orden_ejecucion": orden,
                            "es_calentamiento": es_calentamiento,
                            "repeticion_util": repeticion_util,
                        })
                        writer.writerow(row)
                        stream.flush()
                        n_filas += 1
                        print(
                            f"[{n_filas}] pandas etapa={etapa} orden={orden} "
                            f"calentamiento={es_calentamiento} duracion={row['duracion_segundos']:.3f}s "
                            f"libre_ram_gb={libre:.2f}"
                        )
        except DetencionPorRAM as exc:
            detenido_por_ram = True
            print(f"CORTADO POR RAM: {exc}")

    print(f"CSV escrito ({'append' if modo == 'a' else 'nuevo'}) en {raw_csv} ({n_filas} filas nuevas en esta corrida).")

    if detenido_por_ram:
        raise SystemExit(2)

    if not args.sin_analisis:
        print("Corriendo analizar_experimento.py dentro del contenedor de Spark...")
        run(build_analysis_command(root, args.output))


if __name__ == "__main__":
    main()
