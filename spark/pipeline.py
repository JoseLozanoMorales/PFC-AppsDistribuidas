"""Pipeline analítico reproducible de TiendaTech con PySpark.

Implementa las cinco transformaciones (T1 temporal, T2 filtro, T3 joins, T4
agregación con ventana, T5 Bucketizer) como etapas acumulativas: cada
invocación corre en un proceso/JVM nuevo y ejecuta T1..--etapa, terminando en
una única acción de materialización propia. No hay estado compartido entre
invocaciones (cada proceso es efímero); el .persist() sobre las tablas fuente
es local a ESTE proceso y sirve solo para separar tiempo de lectura JDBC del
tiempo de cómputo de la propia etapa.

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
ETAPAS = ["t1", "t2", "t3", "t4", "t5"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--etapa",
        required=True,
        choices=ETAPAS,
        help="Transformación acumulativa a ejecutar (se corren T1..--etapa en este proceso).",
    )
    parser.add_argument("--master", default="local[4]", help="Ej.: local[1], local[4] o spark://host:7077.")
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
    parser.add_argument(
        "--habilitar-ui",
        action="store_true",
        help="Activa spark.ui.enabled=true (por defecto está apagada) para capturar evidencia visual, "
        "p. ej. el DAG del join en T3.",
    )
    parser.add_argument(
        "--pausa-ui-segundos",
        type=int,
        default=180,
        help="Con --habilitar-ui, segundos que el proceso queda vivo tras terminar la etapa antes de "
        "cerrar la SparkSession, para poder abrir y capturar la UI.",
    )
    parser.add_argument(
        "--particionar-lectura",
        action="store_true",
        help="Lee pedidos.orden y pedidos.detalle_orden con partitionColumn=orden_id "
        "(lowerBound/upperBound/numPartitions) en vez de una sola partición JDBC. Medición "
        "puntual para probar si el cuello de botella es la lectura sin particionar.",
    )
    parser.add_argument(
        "--particiones-lectura",
        type=int,
        default=4,
        help="numPartitions para la lectura JDBC con --particionar-lectura.",
    )
    return parser.parse_args()


def jdbc_options(args: argparse.Namespace) -> dict[str, str]:
    return {
        "url": args.jdbc_url,
        "user": args.jdbc_user,
        "password": args.jdbc_password,
        "driver": DEFAULT_JDBC_DRIVER,
        "fetchsize": "10000",
    }


def read_query(
    spark: SparkSession,
    args: argparse.Namespace,
    query: str,
    *,
    partition_column: str | None = None,
    lower_bound: int | None = None,
    upper_bound: int | None = None,
) -> DataFrame:
    opts = jdbc_options(args)
    if partition_column:
        opts.update({
            "partitionColumn": partition_column,
            "lowerBound": str(lower_bound),
            "upperBound": str(upper_bound),
            "numPartitions": str(args.particiones_lectura),
        })
    return (
        spark.read.format("jdbc")
        .options(**opts)
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
        SparkSession.builder.appName(f"TiendaTech-PFC3-Analitica-{args.etapa}")
        .master(args.master)
        .config("spark.jars", str(jar))
        .config("spark.sql.shuffle.partitions", str(shuffle))
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.ui.enabled", "true" if args.habilitar_ui else "false")
        .getOrCreate()
    )


def read_sources(spark: SparkSession, args: argparse.Namespace) -> tuple[DataFrame, DataFrame, DataFrame]:
    """Lee siempre las 3 tablas fuente, sin importar la etapa: así
    tiempo_jdbc_segundos mide el mismo costo fijo en las 5 etapas y es
    comparable entre ellas (T1 no usa details/users, pero igual se leen)."""
    particion = (
        {"partition_column": "orden_id", "lower_bound": 1, "upper_bound": DATASET_MAX_ORDER_ID}
        if args.particionar_lectura
        else {}
    )
    orders = read_query(
        spark,
        args,
        f"""
        SELECT orden_id, usuario_id, fecha, estado, total
        FROM pedidos.orden
        WHERE orden_id BETWEEN 1 AND {DATASET_MAX_ORDER_ID}
        """,
        **particion,
    )
    details = read_query(
        spark,
        args,
        f"""
        SELECT orden_id, producto_id, cantidad
        FROM pedidos.detalle_orden
        WHERE orden_id BETWEEN 1 AND {DATASET_MAX_ORDER_ID}
        """,
        **particion,
    )
    users = read_query(
        spark,
        args,
        "SELECT usuario_id, habilitado FROM usuarios.usuario",
    )
    return orders, details, users


def apply_t1(orders: DataFrame) -> DataFrame:
    """T1 — Transformación temporal: normalización de fecha y trimestre."""
    return orders.withColumn("fecha", F.to_date("fecha")).withColumn(
        "trimestre", F.concat_ws("-Q", F.year("fecha"), F.quarter("fecha"))
    )


def apply_t2(
    temporal: DataFrame, details: DataFrame, users: DataFrame
) -> tuple[DataFrame, DataFrame, DataFrame]:
    """T2 — Filtro: universo 2026, órdenes no canceladas y datos válidos."""
    filtered_orders = temporal.filter(
        (F.year("fecha") == 2026) & (F.col("estado") != "CANCELADA")
    )
    filtered_details = details.filter(F.col("cantidad") > 0)
    active_users = users.filter(F.col("habilitado"))
    return filtered_orders, filtered_details, active_users


def apply_t3(
    filtered_orders: DataFrame, filtered_details: DataFrame, active_users: DataFrame
) -> DataFrame:
    """T3 — Joins entre tablas relacionadas y colocadas lógicamente."""
    return (
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
    )


def apply_t4(joined: DataFrame, top_n: int) -> tuple[DataFrame, DataFrame]:
    """T4 — Agregación con ventanas: ranking de productos por trimestre."""
    product_totals = joined.groupBy("trimestre", "producto_id").agg(
        F.sum("cantidad").cast("long").alias("unidades"),
        F.countDistinct("orden_id").cast("long").alias("ordenes"),
    )
    ranking = Window.partitionBy("trimestre").orderBy(
        F.desc("unidades"), F.asc("producto_id")
    )
    top_products = (
        product_totals.withColumn("posicion", F.row_number().over(ranking))
        .filter(F.col("posicion") <= top_n)
        .select("trimestre", "posicion", "producto_id", "unidades", "ordenes")
        .orderBy("trimestre", "posicion")
    )
    customer_totals = joined.groupBy("usuario_id").agg(
        F.countDistinct("orden_id").cast("long").alias("frecuencia"),
        F.round(F.sum("total_orden"), 2).alias("gasto_total"),
    )
    return top_products, customer_totals


def apply_t5(customer_totals: DataFrame) -> DataFrame:
    """T5 — Operación ML: Bucketizer para segmentar gasto acumulado.

    Cortes fijos obtenidos de los cuartiles del dataset determinista de 600k.
    """
    splits = [-float("inf"), 33_845.0, 34_518.0, 35_202.0, float("inf")]
    bucketizer = Bucketizer(
        splits=splits,
        inputCol="gasto_total",
        outputCol="segmento_id",
        handleInvalid="keep",
    )
    return (
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


def run_stage(
    spark: SparkSession, args: argparse.Namespace
) -> tuple[dict[str, float], dict[str, int], DataFrame | None, DataFrame | None]:
    """Ejecuta T1..args.etapa de forma acumulativa en este proceso.

    Devuelve (tiempos, counts, top_products, segmented_customers). Los dos
    últimos solo vienen poblados cuando etapa in {t4, t5} / {t5}
    respectivamente; en el resto son None.
    """
    t_jdbc_0 = time.perf_counter()
    orders, details, users = read_sources(spark, args)
    orders = orders.persist(StorageLevel.MEMORY_AND_DISK)
    details = details.persist(StorageLevel.MEMORY_AND_DISK)
    users = users.persist(StorageLevel.MEMORY_AND_DISK)
    counts = {
        "ordenes_fuente": orders.count(),
        "detalles_fuente": details.count(),
        "usuarios_fuente": users.count(),
    }
    t_jdbc_1 = time.perf_counter()

    t_computo_0 = time.perf_counter()
    top_products: DataFrame | None = None
    segmented_customers: DataFrame | None = None

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

    filas_resultado_etapa = result.count()
    t_computo_1 = time.perf_counter()

    tiempos = {
        "tiempo_jdbc_segundos": round(t_jdbc_1 - t_jdbc_0, 6),
        "tiempo_computo_segundos": round(t_computo_1 - t_computo_0, 6),
    }
    counts["filas_resultado_etapa"] = filas_resultado_etapa
    return tiempos, counts, top_products, segmented_customers


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
        tiempos, counts, top_products, segmented_customers = run_stage(spark, args)
        if args.etapa == "t5":
            assert top_products is not None and segmented_customers is not None
            write_outputs(top_products, segmented_customers, args.output, args.overwrite)
        elapsed = time.perf_counter() - started
        cpu_after = process.cpu_times()
        metrics = {
            "motor": "pyspark",
            "etapa": args.etapa,
            "master": args.master,
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
            "spark": spark.version,
            **counts,
        }
        metrics_path.parent.mkdir(parents=True, exist_ok=True)
        metrics_path.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
        print(json.dumps(metrics, indent=2))
        if args.habilitar_ui:
            print(
                f"\nSpark UI activa (spark.ui.enabled=true). Si el puerto 4040 está "
                f"publicado, abre http://localhost:4040 ahora."
            )
            print(f"Pausando {args.pausa_ui_segundos} s antes de cerrar la SparkSession...")
            time.sleep(args.pausa_ui_segundos)
    finally:
        spark.stop()


if __name__ == "__main__":
    main()
