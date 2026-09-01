"""Genera los CSV y figuras finales del Paso 6.

Corre dentro del contenedor de Spark para usar pandas/matplotlib sin tocar
entornos Python del host.
"""

from __future__ import annotations

import argparse
import math
import statistics
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd
from scipy import stats


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--particionado", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def normalizar_bool(serie: pd.Series) -> pd.Series:
    return serie.astype(str).str.lower().isin(["true", "1", "si", "sí"])


def cargar(base_csv: Path, particionado_csv: Path) -> pd.DataFrame:
    base = pd.read_csv(base_csv)
    base["modalidad_lectura"] = "jdbc_sin_particionar"
    base.loc[base["motor"] == "pandas", "modalidad_lectura"] = "baseline_pandas_sql_directo"
    part = pd.read_csv(particionado_csv)
    part["modalidad_lectura"] = "jdbc_particionado_orden_id_4"
    df = pd.concat([base, part], ignore_index=True, sort=False)
    df["es_calentamiento"] = normalizar_bool(df["es_calentamiento"])
    for col in ["ejecutores", "duracion_segundos", "tiempo_jdbc_segundos", "tiempo_computo_segundos"]:
        df[col] = pd.to_numeric(df[col], errors="coerce")
    return df


def describir(grupo: pd.DataFrame) -> pd.Series:
    valores = grupo["duracion_segundos"].dropna().tolist()
    media = statistics.mean(valores)
    mediana = statistics.median(valores)
    if len(valores) > 1:
        sd = stats.tstd(valores)
        margen = stats.t.ppf(0.975, len(valores) - 1) * sd / math.sqrt(len(valores))
    else:
        sd = float("nan")
        margen = float("nan")
    return pd.Series(
        {
            "n": len(valores),
            "media_segundos": round(media, 6),
            "mediana_segundos": round(mediana, 6),
            "desviacion_segundos": round(float(sd), 6),
            "ic95_inferior": round(float(media - margen), 6),
            "ic95_superior": round(float(media + margen), 6),
            "jdbc_media_segundos": round(float(grupo["tiempo_jdbc_segundos"].mean()), 6),
            "computo_media_segundos": round(float(grupo["tiempo_computo_segundos"].mean()), 6),
        }
    )


def amdahl(n: float, p: float) -> float:
    return 1 / ((1 - p) + p / n)


def figura_barras(utiles: pd.DataFrame, output: Path) -> None:
    spark = utiles[(utiles["motor"] == "pyspark") & (utiles["modalidad_lectura"] == "jdbc_sin_particionar")]
    etapas = ["t1", "t2", "t3", "t4", "t5"]
    ejecutores = [1, 2, 4]
    x = list(range(len(etapas)))
    ancho = 0.24

    fig, ax = plt.subplots(figsize=(9, 5))
    for i, ejecutor in enumerate(ejecutores):
        vals = [
            spark[(spark["etapa"] == etapa) & (spark["ejecutores"] == ejecutor)]["duracion_segundos"].median()
            for etapa in etapas
        ]
        ax.bar([xi + (i - 1) * ancho for xi in x], vals, width=ancho, label=f"{ejecutor} ejecutor(es)")
    ax.set_xticks(x)
    ax.set_xticklabels([e.upper() for e in etapas])
    ax.set_ylabel("Duracion total (mediana, s)")
    ax.set_title("Tiempos por etapa en Spark standalone - JDBC sin particionar")
    ax.legend()
    ax.grid(axis="y", alpha=0.25)
    fig.tight_layout()
    fig.savefig(output / "figura_barras_tiempos.png", dpi=300)
    plt.close(fig)


def figura_speedup(utiles: pd.DataFrame, output: Path) -> None:
    part = utiles[
        (utiles["motor"] == "pyspark")
        & (utiles["modalidad_lectura"] == "jdbc_particionado_orden_id_4")
        & (utiles["etapa"] == "t1")
    ]
    ejecutores = [1, 2, 4]
    medianas = {
        n: float(part[part["ejecutores"] == n]["duracion_segundos"].median())
        for n in ejecutores
    }
    speedups = {n: medianas[1] / medianas[n] for n in ejecutores}

    fig, ax = plt.subplots(figsize=(8, 5))
    xs = [1 + i * 3 / 300 for i in range(301)]
    for p in [0.5, 0.75, 0.9, 0.95]:
        ax.plot(xs, [amdahl(n, p) for n in xs], linestyle="--", linewidth=1.5, label=f"Amdahl p={p}")
    ax.plot(ejecutores, [speedups[n] for n in ejecutores], marker="o", linewidth=2.5, label="T1 observado particionado")
    ax.set_xticks(ejecutores)
    ax.set_xlabel("Ejecutores (N)")
    ax.set_ylabel("Speedup S(N) = T_spark(1) / T_spark(N)")
    ax.set_title("Speedup observado vs curvas teoricas de Amdahl")
    ax.grid(alpha=0.25)
    ax.legend()
    fig.tight_layout()
    fig.savefig(output / "figura_speedup_amdahl.png", dpi=300)
    plt.close(fig)


def figura_eficiencia(utiles: pd.DataFrame, output: Path) -> None:
    part = utiles[
        (utiles["motor"] == "pyspark")
        & (utiles["modalidad_lectura"] == "jdbc_particionado_orden_id_4")
        & (utiles["etapa"] == "t1")
    ]
    ejecutores = [1, 2, 4]
    medianas = {
        n: float(part[part["ejecutores"] == n]["duracion_segundos"].median())
        for n in ejecutores
    }
    eficiencias = {n: (medianas[1] / medianas[n]) / n for n in ejecutores}

    fig, ax = plt.subplots(figsize=(7, 4.5))
    ax.bar([str(n) for n in ejecutores], [eficiencias[n] for n in ejecutores], color=["#3B6EA8", "#5C965C", "#C46A40"])
    ax.set_xlabel("Ejecutores (N)")
    ax.set_ylabel("Eficiencia E(N) = S(N) / N")
    ax.set_ylim(0, 1.05)
    ax.set_title("Eficiencia de escalamiento - T1 con JDBC particionado")
    ax.grid(axis="y", alpha=0.25)
    fig.tight_layout()
    fig.savefig(output / "figura_eficiencia.png", dpi=300)
    plt.close(fig)


def main() -> None:
    args = parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    df = cargar(args.base, args.particionado)
    df.to_csv(args.output / "tiempos_crudos.csv", index=False)

    utiles = df[~df["es_calentamiento"]].copy()
    resumen = (
        utiles.groupby(["modalidad_lectura", "motor", "etapa", "ejecutores"], dropna=False)
        .apply(describir)
        .reset_index()
    )
    resumen.to_csv(args.output / "tiempos_resumen.csv", index=False)

    figura_barras(utiles, args.output)
    figura_speedup(utiles, args.output)
    figura_eficiencia(utiles, args.output)
    print(resumen.to_csv(index=False))


if __name__ == "__main__":
    main()
