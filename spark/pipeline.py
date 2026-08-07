"""Pipeline analítico reproducible de TiendaTech con PySpark.

Implementa exactamente las cinco categorías de transformación solicitadas:
T1 temporal, T2 filtro, T3 joins, T4 agregación con ventana y T5 Bucketizer.
El universo reproducible son las órdenes analíticas con ID entre 1 y 600 000.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import shutil
import time
from pathlib import Path

import psutil
from pyspark import StorageLevel
from pyspark.ml.feature import Bucketizer
from pyspark.sql import DataFrame, SparkSession, Window
from pyspark.sql import functions as F


DATASET_MAX_ORDER_ID = 600_000
DEFAULT_JDBC_URL = "jdbc:postgresql://localhost:26257/tiendatech?sslmode=disable"
DEFAULT_JDBC_DRIVER = "org.postgresql.Driver"
DEFAULT_JDBC_JAR = str(
    Path.home()
    / ".m2/repository/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--master", default="local[4]", help="Ej.: local[1], local[2], local[4] o local[8].")
    parser.add_argument("--jdbc-url", default=os.getenv("CRDB_JDBC_URL", DEFAULT_JDBC_URL))
    parser.add_argument("--jdbc-user", default=os.getenv("CRDB_USER", "root"))
    parser.add_argument("--jdbc-password", default=os.getenv("CRDB_PASSWORD", ""))
    parser.add_argument("--jdbc-jar", default=os.getenv("POSTGRES_JDBC_JAR", DEFAULT_JDBC_JAR))
    parser.add_argument("--output", type=Path, default=Path("spark/out/pyspark"))
    parser.add_argument("--metrics", type=Path, default=None)
    parser.add_argument("--run", type=int, default=1)
    parser.add_argument("--top-n", type=int, default=10)
    parser.add_argument("--shuffle-partitions", type=int, default=None)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def jdbc_options(args: argparse.Namespace) -> dict[str, str]:
    return {
        "url": args.jdbc_url,
        "user": args.jdbc_user,
        "password": args.jdbc_password,
        "driver": DEFAULT_JDBC_DRIVER,
        "fetchsize": "10000",
    }


def read_query(spark: SparkSession, args: argparse.Namespace, query: str) -> DataFrame:
    return (
        spark.read.format("jdbc")
        .options(**jdbc_options(args))
        .option("dbtable", f"({query}) AS fuente")
        .load()
    )


def build_spark(args: argparse.Namespace) -> SparkSession:
    jar = Path(args.jdbc_jar).expanduser().resolve()
    if not jar.is_file():
        raise FileNotFoundError(
            f"No se encontró el controlador JDBC en {jar}. "
            "Indique su ubicación con --jdbc-jar o POSTGRES_JDBC_JAR."
        )
    workers = (
        args.master[len("local[") : -1]
        if args.master.startswith("local[") and args.master.endswith("]")
        else args.master
    )
    shuffle = args.shuffle_partitions or (int(workers) * 4 if workers.isdigit() else 16)
    return (
        SparkSession.builder.appName("TiendaTech-PFC3-Analitica")
        .master(args.master)
        .config("spark.jars", str(jar))
        .config("spark.sql.shuffle.partitions", str(shuffle))
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.ui.enabled", "false")
        .getOrCreate()
    )


def execute_pipeline(
    spark: SparkSession, args: argparse.Namespace
) -> tuple[DataFrame, DataFrame, dict[str, int]]:
    orders = read_query(
        spark,
        args,
        f"""
        SELECT orden_id, usuario_id, fecha, estado, total
        FROM pedidos.orden
        WHERE orden_id BETWEEN 1 AND {DATASET_MAX_ORDER_ID}
        """,
    )
    details = read_query(
        spark,
        args,
        f"""
        SELECT orden_id, producto_id, cantidad
        FROM pedidos.detalle_orden
        WHERE orden_id BETWEEN 1 AND {DATASET_MAX_ORDER_ID}
        """,
    )
    users = read_query(
        spark,
        args,
        "SELECT usuario_id, habilitado FROM usuarios.usuario",
    )

    # T1 — Transformación temporal: normalización de fecha y trimestre.
    temporal = orders.withColumn("fecha", F.to_date("fecha")).withColumn(
        "trimestre", F.concat_ws("-Q", F.year("fecha"), F.quarter("fecha"))
    )

    # T2 — Filtro: universo 2026, órdenes no canceladas y datos válidos.
    filtered_orders = temporal.filter(
        (F.year("fecha") == 2026) & (F.col("estado") != "CANCELADA")
    )
    filtered_details = details.filter(F.col("cantidad") > 0)
    active_users = users.filter(F.col("habilitado"))

    # T3 — Joins entre tablas relacionadas y colocadas lógicamente.
    joined = (
        filtered_orders.join(filtered_details, "orden_id", "inner")
        .join(F.broadcast(active_users), "usuario_id", "inner")
        .select(
            "orden_id",
            "usuario_id",
            "trimestre",
            "producto_id",
            "cantidad",
            F.col("total").cast("double").alias("total_orden"),
        )
        .persist(StorageLevel.MEMORY_AND_DISK)
    )

    # T4 — Agregación con ventanas: ranking de productos por trimestre.
    product_totals = joined.groupBy("trimestre", "producto_id").agg(
        F.sum("cantidad").cast("long").alias("unidades"),
        F.countDistinct("orden_id").cast("long").alias("ordenes"),
    )
    ranking = Window.partitionBy("trimestre").orderBy(
        F.desc("unidades"), F.asc("producto_id")
    )
    top_products = (
        product_totals.withColumn("posicion", F.row_number().over(ranking))
        .filter(F.col("posicion") <= args.top_n)
        .select("trimestre", "posicion", "producto_id", "unidades", "ordenes")
        .orderBy("trimestre", "posicion")
    )

    customer_totals = joined.groupBy("usuario_id").agg(
        F.countDistinct("orden_id").cast("long").alias("frecuencia"),
        F.round(F.sum("total_orden"), 2).alias("gasto_total"),
    )

    # T5 — Operación ML: Bucketizer para segmentar gasto acumulado.
    # Cortes fijos obtenidos de los cuartiles del dataset determinista de 600k.
    splits = [-float("inf"), 33_845.0, 34_518.0, 35_202.0, float("inf")]
    bucketizer = Bucketizer(
        splits=splits,
        inputCol="gasto_total",
        outputCol="segmento_id",
        handleInvalid="keep",
    )
    segmented_customers = (
        bucketizer.transform(customer_totals)
        .withColumn(
            "segmento",
            F.element_at(
                F.array(
                    F.lit("BRONCE"),
                    F.lit("PLATA"),
                    F.lit("ORO"),
                    F.lit("PLATINO"),
                    F.lit("SIN_DATO"),
                ),
                F.col("segmento_id").cast("int") + 1,
            ),
        )
        .select("usuario_id", "frecuencia", "gasto_total", "segmento_id", "segmento")
        .orderBy("usuario_id")
    )

    counts = {
        "ordenes_fuente": orders.count(),
        "detalles_fuente": details.count(),
        "usuarios_fuente": users.count(),
        "filas_join": joined.count(),
    }
    return top_products, segmented_customers, counts


def write_outputs(
    top_products: DataFrame,
    segmented_customers: DataFrame,
    output: Path,
    overwrite: bool,
) -> None:
    mode = "overwrite" if overwrite else "errorifexists"
    output.mkdir(parents=True, exist_ok=True)
    top_products.coalesce(1).write.mode(mode).parquet(str(output / "top_productos"))
    segmented_customers.coalesce(1).write.mode(mode).parquet(str(output / "segmentos_clientes"))


def main() -> None:
    args = parse_args()
    if args.overwrite and args.output.exists():
        shutil.rmtree(args.output)
    metrics_path = args.metrics or args.output / "metricas.json"
    process = psutil.Process()
    cpu_before = process.cpu_times()
    memory_before = process.memory_info().rss
    started = time.perf_counter()
    spark = build_spark(args)
    spark.sparkContext.setLogLevel("WARN")
    try:
        top_products, segmented_customers, counts = execute_pipeline(spark, args)
        write_outputs(top_products, segmented_customers, args.output, args.overwrite)
        elapsed = time.perf_counter() - started
        cpu_after = process.cpu_times()
        metrics = {
            "motor": "pyspark",
            "master": args.master,
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
            "spark": spark.version,
            **counts,
        }
        metrics_path.parent.mkdir(parents=True, exist_ok=True)
        metrics_path.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
        print(json.dumps(metrics, indent=2))
    finally:
        spark.stop()


if __name__ == "__main__":
    main()
