"""Baseline pandas comparable con el pipeline PySpark de TiendaTech."""

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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
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


def execute_pipeline(dsn: str, top_n: int) -> tuple[pd.DataFrame, pd.DataFrame, dict[str, int]]:
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

    # T1 — Transformación temporal.
    orders["fecha"] = pd.to_datetime(orders["fecha"])
    orders["trimestre"] = (
        orders["fecha"].dt.year.astype(str) + "-Q" + orders["fecha"].dt.quarter.astype(str)
    )

    # T2 — Filtro.
    filtered_orders = orders[
        (orders["fecha"].dt.year == 2026) & (orders["estado"] != "CANCELADA")
    ]
    filtered_details = details[details["cantidad"] > 0]
    active_users = users[users["habilitado"]]

    # T3 — Joins.
    joined = (
        filtered_orders.merge(filtered_details, on="orden_id", how="inner")
        .merge(active_users, on="usuario_id", how="inner")
    )
    joined["total_orden"] = joined["total"].astype(float)

    # T4 — Agregación y ranking equivalente a la ventana de Spark.
    product_totals = (
        joined.groupby(["trimestre", "producto_id"], as_index=False)
        .agg(unidades=("cantidad", "sum"), ordenes=("orden_id", "nunique"))
        .sort_values(
            ["trimestre", "unidades", "producto_id"],
            ascending=[True, False, True],
        )
    )
    product_totals["posicion"] = (
        product_totals.groupby("trimestre").cumcount() + 1
    )
    top_products = product_totals[product_totals["posicion"] <= top_n][
        ["trimestre", "posicion", "producto_id", "unidades", "ordenes"]
    ].reset_index(drop=True)

    customer_totals = (
        joined.groupby("usuario_id", as_index=False)
        .agg(frecuencia=("orden_id", "nunique"), gasto_total=("total_orden", "sum"))
    )
    customer_totals["gasto_total"] = customer_totals["gasto_total"].round(2)

    # T5 — Bucketizer equivalente mediante cortes fijos.
    customer_totals["segmento_id"] = pd.cut(
        customer_totals["gasto_total"],
        bins=[-float("inf"), 33_845.0, 34_518.0, 35_202.0, float("inf")],
        labels=False,
        right=False,
    ).astype(float)
    customer_totals["segmento"] = customer_totals["segmento_id"].map(
        dict(enumerate(SEGMENT_LABELS))
    )
    segmented_customers = customer_totals[
        ["usuario_id", "frecuencia", "gasto_total", "segmento_id", "segmento"]
    ].sort_values("usuario_id")

    counts = {
        "ordenes_fuente": len(orders),
        "detalles_fuente": len(details),
        "usuarios_fuente": len(users),
        "filas_join": len(joined),
    }
    return top_products, segmented_customers, counts


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
    top_products, segmented_customers, counts = execute_pipeline(args.dsn, args.top_n)
    top_products.to_parquet(args.output / "top_productos.parquet", index=False)
    segmented_customers.to_parquet(
        args.output / "segmentos_clientes.parquet", index=False
    )
    elapsed = time.perf_counter() - started
    cpu_after = process.cpu_times()
    metrics = {
        "motor": "pandas",
        "master": "single-process",
        "repeticion": args.run,
        "duracion_segundos": round(elapsed, 6),
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
