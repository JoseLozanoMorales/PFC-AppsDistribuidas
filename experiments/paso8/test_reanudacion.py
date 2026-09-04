#!/usr/bin/env python3
"""Prueba de reanudacion (no pega a AWS, no usa Locust).

Ejercita el MISMO camino de checkpoint/orden/escritura que usa
run_real_experiment.py (condiciones_en_orden, leer_condiciones_hechas,
escribir_fila) con una ejecutar_corrida falsa que solo duerme unos segundos y
escribe una fila sintetica. Sirve para verificar, matando el proceso a mitad,
que al relanzarlo retoma exactamente donde se corto: sin duplicar filas, sin
saltarse corridas, respetando el orden.
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from run_real_experiment import CSV_FIELDS, condiciones_en_orden, escribir_fila, leer_condiciones_hechas  # noqa: E402


def fila_falsa(fallo: str, coord: str, concurrencia: int, repeticion: int) -> dict:
    fila = {campo: 0 for campo in CSV_FIELDS}
    fila.update(fallo=fallo, coord=coord, concurrencia=concurrencia, repeticion=repeticion)
    return fila


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--limite-corridas", type=int, default=3,
                     help="cuantas corridas del orden real (condiciones_en_orden) procesar en esta prueba")
    ap.add_argument("--segundos-por-corrida", type=float, default=3.0)
    args = ap.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    crudo_path = args.output / "prueba_reanudacion_crudo.csv"
    todas = list(condiciones_en_orden([50, 100], 5))[: args.limite_corridas]
    hechas = leer_condiciones_hechas(crudo_path)
    pendientes = [c for c in todas if c not in hechas]

    print(f"[prueba] {len(hechas)} ya registradas en {crudo_path}, {len(pendientes)} pendientes de {len(todas)} totales")
    for i, (fallo, coord, concurrencia, repeticion) in enumerate(pendientes, start=1):
        print(f"[prueba {i}/{len(pendientes)}] fallo={fallo} coord={coord} c={concurrencia} r={repeticion} -- durmiendo {args.segundos_por_corrida}s")
        time.sleep(args.segundos_por_corrida)
        escribir_fila(crudo_path, fila_falsa(fallo, coord, concurrencia, repeticion))
        print(f"[prueba {i}/{len(pendientes)}] escrita")

    print("prueba completa" if not pendientes else "nada pendiente")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
