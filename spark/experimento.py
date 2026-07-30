"""Orquesta las repeticiones y genera el resumen estadístico de D4."""

from __future__ import annotations

import argparse
import csv
import json
import math
import platform
import subprocess
import sys
from pathlib import Path

from scipy import stats


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repeticiones", type=int, default=10)
    parser.add_argument("--workers", type=int, nargs="+", default=[1, 2, 4, 8])
    parser.add_argument("--output", type=Path, default=Path("spark/out/experimento"))
    parser.add_argument("--jdbc-jar", default=None)
    parser.add_argument("--incluir-pandas", action="store_true")
    parser.add_argument(
        "--spark-directo",
        action="store_true",
        help="No usar el contenedor Linux para Spark, incluso en Windows.",
    )
    return parser.parse_args()


def run(command: list[str]) -> None:
    subprocess.run(command, check=True)


def describe(values: list[float]) -> dict[str, float | int]:
    if len(values) < 2:
        raise ValueError("Se requieren al menos dos observaciones.")
    mean = sum(values) / len(values)
    std = stats.tstd(values)
    margin = stats.t.ppf(0.975, len(values) - 1) * std / math.sqrt(len(values))
    return {
        "n": len(values),
        "media_segundos": round(mean, 6),
        "desviacion_segundos": round(float(std), 6),
        "ic95_inferior": round(float(mean - margin), 6),
        "ic95_superior": round(float(mean + margin), 6),
    }


def paired_test(reference: list[float], candidate: list[float]) -> dict[str, float | str]:
    size = min(len(reference), len(candidate))
    reference, candidate = reference[:size], candidate[:size]
    differences = [a - b for a, b in zip(reference, candidate)]
    normality_p = float(stats.shapiro(differences).pvalue)
    if normality_p >= 0.05:
        result = stats.ttest_rel(reference, candidate)
        name = "t_pareada"
    else:
        result = stats.wilcoxon(reference, candidate)
        name = "wilcoxon"
    return {
        "prueba": name,
        "normalidad_shapiro_p": round(normality_p, 8),
        "estadistico": round(float(result.statistic), 8),
        "p_valor": round(float(result.pvalue), 8),
    }


def main() -> None:
    args = parse_args()
    if args.repeticiones < 4:
        raise ValueError("Use al menos 4 repeticiones para descartar primera y última.")
    root = Path(__file__).resolve().parent
    args.output.mkdir(parents=True, exist_ok=True)
    raw_rows: list[dict] = []

    configurations: list[tuple[str, list[str], int | None]] = [
        (
            f"local[{workers}]",
            [
                sys.executable,
                str(root / "pipeline.py"),
                "--master",
                f"local[{workers}]",
            ],
            workers,
        )
        for workers in args.workers
    ]
    if args.incluir_pandas:
        configurations.append(
            ("pandas", [sys.executable, str(root / "baseline.py")], None)
        )

    docker_spark = platform.system() == "Windows" and not args.spark_directo
    spark_output_root = (root / "out").resolve()
    for configuration, base_command, workers in configurations:
        for repetition in range(1, args.repeticiones + 1):
            run_dir = (
                args.output
                / configuration.replace("[", "").replace("]", "")
                / f"r{repetition:02d}"
            ).resolve()
            metrics = run_dir / "metricas.json"
            if docker_spark and workers is not None:
                try:
                    container_output = run_dir.relative_to(spark_output_root)
                except ValueError as error:
                    raise ValueError(
                        "En Windows, --output debe estar dentro de spark/out."
                    ) from error
                command = [
                    "powershell",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    str(root / "ejecutar-pyspark.ps1"),
                    "-Workers",
                    str(workers),
                    "-Repeticion",
                    str(repetition),
                    "-Salida",
                    container_output.as_posix(),
                ]
            else:
                command = [
                    *base_command,
                    "--run",
                    str(repetition),
                    "--output",
                    str(run_dir),
                    "--metrics",
                    str(metrics),
                    "--overwrite",
                ]
                if args.jdbc_jar and workers is not None:
                    command.extend(["--jdbc-jar", args.jdbc_jar])
            run(command)
            raw_rows.append(json.loads(metrics.read_text(encoding="utf-8")))

    raw_csv = args.output / "mediciones.csv"
    with raw_csv.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=sorted({key for row in raw_rows for key in row}))
        writer.writeheader()
        writer.writerows(raw_rows)

    durations: dict[str, list[float]] = {}
    for row in raw_rows:
        durations.setdefault(str(row["master"]), []).append(float(row["duracion_segundos"]))
    trimmed = {name: values[1:-1] for name, values in durations.items()}
    summary = {name: describe(values) for name, values in trimmed.items()}
    reference_name = f"local[{args.workers[0]}]"
    tests = {
        f"{reference_name}_vs_{name}": paired_test(trimmed[reference_name], values)
        for name, values in trimmed.items()
        if name != reference_name
    }
    result = {
        "criterio_descarte": "primera y última repetición de cada configuración",
        "resumen": summary,
        "pruebas_pareadas": tests,
    }
    (args.output / "resumen.json").write_text(
        json.dumps(result, indent=2), encoding="utf-8"
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
