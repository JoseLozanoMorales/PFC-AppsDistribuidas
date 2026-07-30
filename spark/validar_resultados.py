"""Valida igualdad numérica entre las salidas Parquet de pandas y PySpark."""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spark", type=Path, default=Path("spark/out/pyspark"))
    parser.add_argument("--pandas", type=Path, default=Path("spark/out/pandas"))
    return parser.parse_args()


def normalize(frame: pd.DataFrame, keys: list[str]) -> pd.DataFrame:
    return frame.sort_values(keys).reset_index(drop=True).sort_index(axis=1)


def main() -> None:
    args = parse_args()
    pairs = [
        ("top_productos", ["trimestre", "posicion"]),
        ("segmentos_clientes", ["usuario_id"]),
    ]
    for name, keys in pairs:
        spark_frame = pd.read_parquet(args.spark / name)
        pandas_frame = pd.read_parquet(args.pandas / f"{name}.parquet")
        pd.testing.assert_frame_equal(
            normalize(spark_frame, keys),
            normalize(pandas_frame, keys),
            check_dtype=False,
            check_exact=False,
            rtol=1e-9,
            atol=1e-6,
        )
        print(f"OK: {name} ({len(spark_frame)} filas)")


if __name__ == "__main__":
    main()
