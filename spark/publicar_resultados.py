"""Publica la evidencia compacta del experimento sin versionar spark/out/."""

from __future__ import annotations

import argparse
import json
import os
import shutil
from pathlib import Path

_CACHE = Path("spark/out/.matplotlib").resolve()
_CACHE.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("MPLCONFIGDIR", str(_CACHE))

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("spark/out/experimento"),
        help="Directorio generado por experimento.py.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("docs/experimentos/resultados"),
        help="Directorio versionable de evidencia compacta.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    source_csv = args.input / "mediciones.csv"
    source_summary = args.input / "resumen.json"
    if not source_csv.is_file() or not source_summary.is_file():
        raise FileNotFoundError(
            "Ejecute spark/experimento.py antes de publicar los resultados."
        )

    data = pd.read_csv(source_csv)
    required = {"master", "duracion_segundos", "repeticion"}
    missing = required - set(data.columns)
    if missing:
        raise ValueError(f"Faltan columnas requeridas: {sorted(missing)}")

    args.output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source_csv, args.output / "raw.csv")
    shutil.copyfile(source_summary, args.output / "resumen.json")

    order = ["single-process", "local[1]", "local[2]", "local[4]", "local[8]"]
    series = [
        data.loc[data["master"] == configuration, "duracion_segundos"].tolist()
        for configuration in order
    ]
    if any(len(values) != 10 for values in series):
        raise ValueError("Se esperaban 10 repeticiones para cada configuración.")

    plt.figure(figsize=(9, 5.2))
    plot = plt.boxplot(series, tick_labels=order, patch_artist=True, showmeans=True)
    for box in plot["boxes"]:
        box.set_facecolor("#8ecae6")
        box.set_edgecolor("#165a72")
    plt.ylabel("Tiempo total (segundos)")
    plt.xlabel("Motor y configuración")
    plt.title("Distribución del tiempo de ejecución (10 repeticiones)")
    plt.grid(axis="y", alpha=0.25)
    plt.tight_layout()
    plt.savefig(args.output / "boxplot.png", dpi=180)
    plt.close()

    summary = json.loads(source_summary.read_text(encoding="utf-8"))
    if len(summary.get("resumen", {})) != 5:
        raise ValueError("El resumen no contiene las cinco configuraciones esperadas.")


if __name__ == "__main__":
    main()
