"""Baseline pandas comparable con el pipeline PySpark de TiendaTech.

Mismo diseño que pipeline.py: cada invocación ejecuta T1..--etapa de forma
acumulativa en un proceso nuevo, sin estado compartido con otras etapas.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import shutil
import time
from pathlib import Path

import pandas as pd
import psutil
import psycopg


DATASET_MAX_ORDER_ID = 600_000
DEFAULT_DSN = "postgresql://root@localhost:26257/tiendatech?sslmode=disable"
SEGMENT_LABELS = ["BRONCE", "PLATA", "ORO", "PLATINO"]
ETAPAS = ["t1", "t2", "t3", "t4", "t5"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--etapa",
        required=True,
        choices=ETAPAS,
        help="Transformación acumulativa a ejecutar (se corren T1..--etapa en este proceso).",
    )
    parser.add_argument("--dsn", default=os.getenv("CRDB_DSN", DEFAULT_DSN))
    parser.add_argument("--output", type=Path, default=Path("spark/out/pandas"))
    parser.add_argument("--metrics", type=Path, default=None)
    parser.add_argument("--run", type=int, default=1)
    parser.add_argument("--top-n", type=int, default=10)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def read_frame(connection: psycopg.Connection, query: str) -> pd.DataFrame:
    with connection.cursor() as cursor:
        cursor.execute(query)
        columns = [column.name for column in cursor.description]
        return pd.DataFrame(cursor.fetchall(), columns=columns)


def read_sources(dsn: str) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """Lee siempre las 3 tablas fuente, sin importar la etapa: así
    tiempo_jdbc_segundos mide el mismo costo fijo en las 5 etapas."""
    with psycopg.connect(dsn) as connection:
        orders = read_frame(
            connection,
            f"""
            SELECT orden_id, usuario_id, fecha, estado, total
            FROM pedidos.orden
            WHERE orden_id BETWEEN 1 AND {DATASET_MAX_ORDER_ID}
            """,
        )
        details = read_frame(
            connection,
            f"""
            SELECT orden_id, producto_id, cantidad
            FROM pedidos.detalle_orden
            WHERE orden_id BETWEEN 1 AND {DATASET_MAX_ORDER_ID}
            """,
        )
        users = read_frame(
            connection,
            "SELECT usuario_id, habilitado FROM usuarios.usuario",
        )
    return orders, details, users


def apply_t1(orders: pd.DataFrame) -> pd.DataFrame:
    """T1 — Transformación temporal."""
    temporal = orders.copy()
    temporal["fecha"] = pd.to_datetime(temporal["fecha"])
    temporal["trimestre"] = (
        temporal["fecha"].dt.year.astype(str) + "-Q" + temporal["fecha"].dt.quarter.astype(str)
    )
    return temporal


def apply_t2(
    temporal: pd.DataFrame, details: pd.DataFrame, users: pd.DataFrame
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """T2 — Filtro."""
    filtered_orders = temporal[
        (temporal["fecha"].dt.year == 2026) & (temporal["estado"] != "CANCELADA")
    ]
    filtered_details = details[details["cantidad"] > 0]
    active_users = users[users["habilitado"]]
    return filtered_orders, filtered_details, active_users


def apply_t3(
    filtered_orders: pd.DataFrame, filtered_details: pd.DataFrame, active_users: pd.DataFrame
) -> pd.DataFrame:
    """T3 — Joins."""
    joined = filtered_orders.merge(filtered_details, on="orden_id", how="inner").merge(
        active_users, on="usuario_id", how="inner"
    )
    joined["total_orden"] = joined["total"].astype(float)
    return joined


def apply_t4(joined: pd.DataFrame, top_n: int) -> tuple[pd.DataFrame, pd.DataFrame]:
    """T4 — Agregación y ranking equivalente a la ventana de Spark."""
    product_totals = (
        joined.groupby(["trimestre", "producto_id"], as_index=False)
        .agg(unidades=("cantidad", "sum"), ordenes=("orden_id", "nunique"))
        .sort_values(
            ["trimestre", "unidades", "producto_id"],
            ascending=[True, False, True],
        )
    )
    product_totals["posicion"] = product_totals.groupby("trimestre").cumcount() + 1
    top_products = product_totals[product_totals["posicion"] <= top_n][
        ["trimestre", "posicion", "producto_id", "unidades", "ordenes"]
    ].reset_index(drop=True)

    customer_totals = joined.groupby("usuario_id", as_index=False).agg(
        frecuencia=("orden_id", "nunique"), gasto_total=("total_orden", "sum")
    )
    customer_totals["gasto_total"] = customer_totals["gasto_total"].round(2)
    return top_products, customer_totals


def apply_t5(customer_totals: pd.DataFrame) -> pd.DataFrame:
    """T5 — Bucketizer equivalente mediante cortes fijos."""
    segmented = customer_totals.copy()
    segmented["segmento_id"] = pd.cut(
        segmented["gasto_total"],
        bins=[-float("inf"), 33_845.0, 34_518.0, 35_202.0, float("inf")],
        labels=False,
        right=False,
    ).astype(float)
    segmented["segmento"] = segmented["segmento_id"].map(dict(enumerate(SEGMENT_LABELS)))
    return segmented[["usuario_id", "frecuencia", "gasto_total", "segmento_id", "segmento"]].sort_values(
        "usuario_id"
    )


def run_stage(
    args: argparse.Namespace,
) -> tuple[dict[str, float], dict[str, int], pd.DataFrame | None, pd.DataFrame | None]:
    t_jdbc_0 = time.perf_counter()
    orders, details, users = read_sources(args.dsn)
    t_jdbc_1 = time.perf_counter()
    counts = {
        "ordenes_fuente": len(orders),
        "detalles_fuente": len(details),
        "usuarios_fuente": len(users),
    }

    t_computo_0 = time.perf_counter()
    top_products: pd.DataFrame | None = None
    segmented_customers: pd.DataFrame | None = None

    temporal = apply_t1(orders)
    result = temporal
    if args.etapa != "t1":
        filtered_orders, filtered_details, active_users = apply_t2(temporal, details, users)
        result = filtered_orders
        if args.etapa != "t2":
            joined = apply_t3(filtered_orders, filtered_details, active_users)
            result = joined
            if args.etapa != "t3":
                top_products, customer_totals = apply_t4(joined, args.top_n)
                result = top_products
                if args.etapa != "t4":
                    segmented_customers = apply_t5(customer_totals)
                    result = segmented_customers

    filas_resultado_etapa = len(result)
    t_computo_1 = time.perf_counter()

    tiempos = {
        "tiempo_jdbc_segundos": round(t_jdbc_1 - t_jdbc_0, 6),
        "tiempo_computo_segundos": round(t_computo_1 - t_computo_0, 6),
    }
    counts["filas_resultado_etapa"] = filas_resultado_etapa
    return tiempos, counts, top_products, segmented_customers


def main() -> None:
    args = parse_args()
    if args.overwrite and args.output.exists():
        shutil.rmtree(args.output)
    if args.output.exists() and not args.overwrite:
        raise FileExistsError(f"{args.output} ya existe; use --overwrite.")
    args.output.mkdir(parents=True)
    metrics_path = args.metrics or args.output / "metricas.json"
    process = psutil.Process()
    cpu_before = process.cpu_times()
    memory_before = process.memory_info().rss
    started = time.perf_counter()
    tiempos, counts, top_products, segmented_customers = run_stage(args)
    if args.etapa == "t5":
        assert top_products is not None and segmented_customers is not None
        top_products.to_parquet(args.output / "top_productos.parquet", index=False)
        segmented_customers.to_parquet(args.output / "segmentos_clientes.parquet", index=False)
    elapsed = time.perf_counter() - started
    cpu_after = process.cpu_times()
    metrics = {
        "motor": "pandas",
        "etapa": args.etapa,
        "master": "single-process",
        "repeticion": args.run,
        "duracion_segundos": round(elapsed, 6),
        **tiempos,
        "cpu_segundos": round(
            (cpu_after.user + cpu_after.system) - (cpu_before.user + cpu_before.system),
            6,
        ),
        "rss_inicial_mb": round(memory_before / 1024**2, 3),
        "rss_final_mb": round(process.memory_info().rss / 1024**2, 3),
        "dataset_max_order_id": DATASET_MAX_ORDER_ID,
        "python": platform.python_version(),
        "pandas": pd.__version__,
        **counts,
    }
    metrics_path.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    main()
