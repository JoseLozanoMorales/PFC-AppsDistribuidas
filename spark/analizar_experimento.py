"""Analiza mediciones.csv del Paso 6: estadística (mediana, IC95, prueba
pareada), speedup y delta por etapa, más figuras.

Diseñado para correr DENTRO del contenedor de Spark (tiendatech-spark:3.5.5),
que ya trae pandas/scipy/matplotlib — nunca en el Python del host ni en un
venv de otro proyecto (ver política en spark/PLAN-PASO6.md).
"""

from __future__ import annotations

import argparse
import json
import math
import statistics
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd
from scipy import stats


NOTA_VALIDEZ_INTERNA = (
    "Tanto pipeline.py (PySpark) como baseline.py (pandas) se lanzan de la misma manera: "
    "`docker run --rm` desde experimento.py, con el mismo overhead de arranque/derribo de "
    "contenedor (~4 s por corrida, medido). No hay asimetría de 'pandas en el host vs Spark en "
    "contenedor' -- ambos corren contenerizados. La asimetría real y esperable es otra: el "
    "tiempo de Spark incluye arranque de JVM + conexión al cluster standalone (varios segundos "
    "fijos), algo que pandas no paga. Esto es inherente a comparar un motor distribuido real "
    "contra un proceso secuencial, no un artefacto de medición a corregir -- de hecho es parte "
    "de lo que el speedup S = T_secuencial / T_distribuido debe reflejar honestamente."
)

NOTA_METODOLOGICA = (
    "Cada etapa Tk se mide con su propia corrida (spark-submit nuevo, sin persistencia "
    "compartida con otras etapas) ejecutando T1..Tk de forma acumulativa. El 'tiempo bruto' "
    "de cada etapa por lo tanto ACUMULA el costo de las etapas previas MAS una lectura JDBC "
    "repetida de las 3 tablas fuente en cada corrida (read_sources() se ejecuta siempre "
    "completo, sin importar la etapa). Esto es intencional, no un artefacto a corregir: la "
    "lectura JDBC repetida y el cómputo de etapas previas son parte de la fracción no "
    "escalable observada en este hardware (decisión 5 del plan). 'delta_por_etapa' reporta "
    "el costo marginal medido (diferencia de medianas entre etapas consecutivas de corridas "
    "reales, no una resta derivada de una sola corrida completa)."
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="Ruta a mediciones.csv")
    parser.add_argument("--output", type=Path, required=True, help="Directorio para resumen.json y figuras")
    parser.add_argument(
        "--ejecutores-base",
        type=int,
        default=None,
        help="Ejecutores de referencia para speedup (por defecto, el menor presente).",
    )
    return parser.parse_args()


def describe(values: list[float]) -> dict[str, float | int]:
    if len(values) < 2:
        raise ValueError("Se requieren al menos dos observaciones útiles.")
    mean = sum(values) / len(values)
    median = statistics.median(values)
    std = stats.tstd(values)
    margin = stats.t.ppf(0.975, len(values) - 1) * std / math.sqrt(len(values))
    return {
        "n": len(values),
        "mediana_segundos": round(median, 6),
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


def cargar(input_csv: Path) -> tuple[pd.DataFrame, pd.DataFrame]:
    df = pd.read_csv(input_csv)
    df["es_calentamiento"] = df["es_calentamiento"].astype(str).str.lower().isin(["true", "1"])
    utiles = df[~df["es_calentamiento"]].copy()
    calentamientos = df[df["es_calentamiento"]].copy()
    return utiles, calentamientos


def construir_resumen(
    utiles: pd.DataFrame, calentamientos: pd.DataFrame, ejecutores_base: int | None
) -> dict:
    etapas = sorted(utiles["etapa"].dropna().unique())
    ejec_presentes = sorted(
        int(e) for e in utiles.loc[utiles["motor"] == "pyspark", "ejecutores"].dropna().unique()
    )
    base = ejecutores_base or (ejec_presentes[0] if ejec_presentes else None)

    resumen: dict[str, dict] = {}
    for (motor, etapa, ejecutores), grupo in utiles.groupby(["motor", "etapa", "ejecutores"], dropna=False):
        clave = f"{motor}_{etapa}_{'na' if pd.isna(ejecutores) else int(ejecutores)}"
        if pd.isna(ejecutores):
            cal = calentamientos[(calentamientos["motor"] == motor) & (calentamientos["etapa"] == etapa)]
        else:
            cal = calentamientos[
                (calentamientos["motor"] == motor)
                & (calentamientos["etapa"] == etapa)
                & (calentamientos["ejecutores"] == ejecutores)
            ]
        resumen[clave] = {
            "descartada_calentamiento_segundos": (
                round(float(cal["duracion_segundos"].iloc[0]), 6) if len(cal) else None
            ),
            "duracion_total": describe(grupo["duracion_segundos"].tolist()),
            "tiempo_jdbc": describe(grupo["tiempo_jdbc_segundos"].tolist()),
            "tiempo_computo": describe(grupo["tiempo_computo_segundos"].tolist()),
        }

    deltas: dict[str, dict] = {}
    for ejecutores in ejec_presentes:
        anterior: float | None = None
        anterior_etapa: str | None = None
        for etapa in etapas:
            sub = utiles[
                (utiles["motor"] == "pyspark")
                & (utiles["etapa"] == etapa)
                & (utiles["ejecutores"] == ejecutores)
            ]
            if sub.empty:
                continue
            mediana = float(sub["duracion_segundos"].median())
            if anterior is not None:
                deltas[f"{ejecutores}ej_{anterior_etapa}_a_{etapa}"] = {
                    "delta_segundos": round(mediana - anterior, 6),
                    "tiempo_bruto_etapa_segundos": round(mediana, 6),
                    "tiempo_bruto_etapa_anterior_segundos": round(anterior, 6),
                }
            anterior, anterior_etapa = mediana, etapa

    speedups: dict[str, dict] = {}
    if base is not None:
        for etapa in etapas:
            base_sub = utiles[
                (utiles["motor"] == "pyspark") & (utiles["etapa"] == etapa) & (utiles["ejecutores"] == base)
            ].sort_values("repeticion_util")
            if base_sub.empty:
                continue
            for ejecutores in ejec_presentes:
                if ejecutores == base:
                    continue
                cand_sub = utiles[
                    (utiles["motor"] == "pyspark")
                    & (utiles["etapa"] == etapa)
                    & (utiles["ejecutores"] == ejecutores)
                ].sort_values("repeticion_util")
                if cand_sub.empty:
                    continue
                nombre = f"{etapa}_{base}ej_vs_{ejecutores}ej"
                base_dur = base_sub["duracion_segundos"].tolist()
                cand_dur = cand_sub["duracion_segundos"].tolist()
                base_comp = base_sub["tiempo_computo_segundos"].tolist()
                cand_comp = cand_sub["tiempo_computo_segundos"].tolist()
                speedups[nombre] = {
                    "speedup_duracion_total_mediana": round(
                        statistics.median(base_dur) / statistics.median(cand_dur), 4
                    ),
                    "speedup_tiempo_computo_mediana": round(
                        statistics.median(base_comp) / statistics.median(cand_comp), 4
                    ),
                    "prueba_pareada_tiempo_computo": paired_test(base_comp, cand_comp),
                }

    # Comparación de TECNOLOGÍAS (Spark vs pandas), S = T_pandas / T_spark.
    # Deliberadamente separada de 'speedup_por_etapa' (escalabilidad interna de
    # Spark, T_spark(1)/T_spark(N)): son dos preguntas distintas y no se
    # comparan contra las curvas teóricas de Amdahl, que solo aplican a la
    # escalabilidad interna de un mismo motor.
    spark_vs_pandas: dict[str, dict] = {}
    for etapa in etapas:
        pandas_sub = utiles[(utiles["motor"] == "pandas") & (utiles["etapa"] == etapa)]
        if pandas_sub.empty:
            continue
        mediana_pandas = float(pandas_sub["duracion_segundos"].median())
        for ejecutores in ejec_presentes:
            spark_sub = utiles[
                (utiles["motor"] == "pyspark") & (utiles["etapa"] == etapa) & (utiles["ejecutores"] == ejecutores)
            ]
            if spark_sub.empty:
                continue
            mediana_spark = float(spark_sub["duracion_segundos"].median())
            spark_vs_pandas[f"{etapa}_pandas_vs_spark_{ejecutores}ej"] = {
                "mediana_pandas_segundos": round(mediana_pandas, 6),
                "mediana_spark_segundos": round(mediana_spark, 6),
                "speedup_S_Tsec_sobre_Tdist": round(mediana_pandas / mediana_spark, 4),
            }

    n_utiles = int(utiles.groupby(["motor", "etapa", "ejecutores"], dropna=False).size().max()) if len(utiles) else 0
    return {
        "criterio_repeticiones": (
            f"1 ejecución de calentamiento descartada + {n_utiles} mediciones útiles "
            "por combinación etapa x ejecutores; se reporta la mediana."
        ),
        "nota_metodologica": NOTA_METODOLOGICA,
        "nota_validez_interna": NOTA_VALIDEZ_INTERNA,
        "resumen": resumen,
        "delta_por_etapa": deltas,
        "speedup_por_etapa_escalabilidad_interna_spark": speedups,
        "speedup_spark_vs_pandas_comparacion_tecnologias": spark_vs_pandas,
    }


def generar_figuras(utiles: pd.DataFrame, output: Path) -> None:
    pyspark = utiles[utiles["motor"] == "pyspark"]
    if pyspark.empty:
        return
    etapas = sorted(pyspark["etapa"].dropna().unique())
    ejecutores = sorted(int(e) for e in pyspark["ejecutores"].dropna().unique())

    DPI = 300

    fig, ax = plt.subplots(figsize=(8, 5))
    for ejecutor in ejecutores:
        medianas = [
            pyspark[(pyspark["etapa"] == etapa) & (pyspark["ejecutores"] == ejecutor)][
                "duracion_segundos"
            ].median()
            for etapa in etapas
        ]
        ax.plot(etapas, medianas, marker="o", label=f"{ejecutor} ejecutor(es)")
    ax.set_xlabel("Etapa")
    ax.set_ylabel("Duración total (mediana, s)")
    ax.set_title("Duración total por etapa y número de ejecutores (Spark)")
    ax.legend()
    fig.tight_layout()
    fig.savefig(output / "duracion_por_etapa.png", dpi=DPI)
    plt.close(fig)

    ejecutor_ref = max(ejecutores) if ejecutores else None
    if ejecutor_ref is not None:
        jdbc_medianas = []
        computo_medianas = []
        for etapa in etapas:
            sub = pyspark[(pyspark["etapa"] == etapa) & (pyspark["ejecutores"] == ejecutor_ref)]
            jdbc_medianas.append(sub["tiempo_jdbc_segundos"].median())
            computo_medianas.append(sub["tiempo_computo_segundos"].median())
        fig, ax = plt.subplots(figsize=(8, 5))
        x = list(range(len(etapas)))
        ax.bar(x, jdbc_medianas, label="JDBC", color="#4C72B0")
        ax.bar(x, computo_medianas, bottom=jdbc_medianas, label="Cómputo", color="#DD8452")
        ax.set_xticks(x)
        ax.set_xticklabels(etapas)
        ax.set_ylabel("Segundos (mediana)")
        ax.set_title(f"Lectura JDBC vs cómputo por etapa ({ejecutor_ref} ejecutores, Spark)")
        ax.legend()
        fig.tight_layout()
        fig.savefig(output / "jdbc_vs_computo.png", dpi=DPI)
        plt.close(fig)

    # Figura 3 (LA que pide la rúbrica para Amdahl): S(N) OBSERVADO de Spark
    # contra Spark mismo (T_spark(1)/T_spark(N)) vs curvas teóricas de Amdahl.
    # Nunca contra pandas -- esa es una comparación de tecnologías distinta
    # (ver figura 4).
    if len(ejecutores) >= 2:
        n_min = min(ejecutores)
        n_max = max(ejecutores)
        n_continuo = [n_min + i * (n_max - n_min) / 200 for i in range(201)] if n_max > n_min else [n_min]

        fig, ax = plt.subplots(figsize=(8, 5.5))
        for p in [0.5, 0.75, 0.9, 0.95]:
            curva = [1 / ((1 - p) + p / n) for n in n_continuo]
            ax.plot(n_continuo, curva, linestyle="--", alpha=0.6, label=f"Amdahl teórico p={p}")

        for etapa in etapas:
            base_sub = pyspark[(pyspark["etapa"] == etapa) & (pyspark["ejecutores"] == n_min)]
            if base_sub.empty:
                continue
            base_mediana = base_sub["duracion_segundos"].median()
            xs, ys = [], []
            for n in ejecutores:
                sub = pyspark[(pyspark["etapa"] == etapa) & (pyspark["ejecutores"] == n)]
                if sub.empty:
                    continue
                xs.append(n)
                ys.append(base_mediana / sub["duracion_segundos"].median())
            ax.plot(xs, ys, marker="o", linewidth=2, label=f"{etapa} observado (Spark)")

        ax.set_xlabel(f"Ejecutores (N) -- referencia S(1)=1 en N={n_min}")
        ax.set_ylabel("Speedup S(N) = T_spark(N_min) / T_spark(N)")
        ax.set_title("Escalabilidad interna de Spark vs curvas teóricas de Amdahl\n(NO es una comparación contra pandas)")
        ax.legend(fontsize=8, ncol=2)
        fig.tight_layout()
        fig.savefig(output / "amdahl_vs_spark.png", dpi=DPI)
        plt.close(fig)

    # Figura 4: Spark vs pandas -- comparación de TECNOLOGÍAS, no de escalado.
    # A propósito en una figura aparte de la de Amdahl (figura 3).
    pandas_df = utiles[utiles["motor"] == "pandas"]
    if not pandas_df.empty and ejecutores:
        fig, ax = plt.subplots(figsize=(8, 5))
        x = list(range(len(etapas)))
        ancho = 0.8 / (len(ejecutores) + 1)
        pandas_medianas = [pandas_df[pandas_df["etapa"] == etapa]["duracion_segundos"].median() for etapa in etapas]
        ax.bar(
            [xi - 0.4 + ancho / 2 for xi in x], pandas_medianas, width=ancho, label="pandas (secuencial)", color="#555555"
        )
        for i, ejecutor in enumerate(ejecutores, start=1):
            medianas = [
                pyspark[(pyspark["etapa"] == etapa) & (pyspark["ejecutores"] == ejecutor)]["duracion_segundos"].median()
                for etapa in etapas
            ]
            ax.bar(
                [xi - 0.4 + ancho / 2 + i * ancho for xi in x],
                medianas,
                width=ancho,
                label=f"Spark {ejecutor} ejecutor(es)",
            )
        ax.set_xticks(x)
        ax.set_xticklabels(etapas)
        ax.set_ylabel("Duración total (mediana, s)")
        ax.set_title("Spark vs pandas por etapa -- comparación de tecnologías\n(S = T_pandas / T_spark, no escalabilidad)")
        ax.legend(fontsize=8)
        fig.tight_layout()
        fig.savefig(output / "spark_vs_pandas.png", dpi=DPI)
        plt.close(fig)


def main() -> None:
    args = parse_args()
    utiles, calentamientos = cargar(args.input)
    resultado = construir_resumen(utiles, calentamientos, args.ejecutores_base)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "resumen.json").write_text(
        json.dumps(resultado, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    generar_figuras(utiles, args.output)
    print(json.dumps(resultado, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
