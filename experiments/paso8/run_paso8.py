#!/usr/bin/env python3
"""Ejecuta y analiza el experimento del Paso 8 para TiendaTech.

Usa el banco de Paso 7 como sistema bajo prueba: E-2PC y E-SAGA, inyector de
fallos de pasarela y oraculo de consistencia. No depende de paquetes externos.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import statistics
import sys
import time
import tracemalloc
from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "experiments" / "paso7"))

from coordination_lab import Case, FaultInjector, Lab, compatibility_cases, make_cases, oracle  # noqa: E402


COORDS = ["2pc", "saga"]
CONCURRENCIAS = [50, 100, 200, 400]
FALLOS = ["none", "omission", "timing"]
CSV_FIELDS = [
    "coord",
    "concurrencia",
    "fallo",
    "repeticion",
    "seed",
    "warmup_seconds",
    "delay_seconds",
    "fault_probability",
    "operaciones",
    "confirmadas",
    "abortadas",
    "inconsistencias",
    "tasa_inconsistencia",
    "tasa_abortos",
    "latencia_pago_p50_ms",
    "latencia_pago_p95_ms",
    "latencia_pago_p99_ms",
    "ordenes_confirmadas_por_segundo",
    "convergencia_compensacion_ms",
    "cpu_segundos",
    "memoria_pico_mb",
    "duracion_segundos",
    "oracle_pass",
]


def percentile(values: list[float], q: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    pos = (len(ordered) - 1) * q
    low = math.floor(pos)
    high = math.ceil(pos)
    if low == high:
        return ordered[low]
    return ordered[low] + (ordered[high] - ordered[low]) * (pos - low)


def wilson_interval(successes: int, n: int, z: float = 1.959963984540054) -> tuple[float, float]:
    if n == 0:
        return 0.0, 0.0
    phat = successes / n
    denom = 1 + z * z / n
    center = (phat + z * z / (2 * n)) / denom
    margin = z * math.sqrt((phat * (1 - phat) + z * z / (4 * n)) / n) / denom
    return max(0.0, center - margin), min(1.0, center + margin)


def bootstrap_median_ci(values: list[float], seed: int = 2026, samples: int = 1000) -> tuple[float, float]:
    if not values:
        return 0.0, 0.0
    import random

    rng = random.Random(seed)
    medians = []
    for _ in range(samples):
        sample = [values[rng.randrange(len(values))] for _ in values]
        medians.append(statistics.median(sample))
    return percentile(medians, 0.025), percentile(medians, 0.975)


def mann_whitney_u(a: list[float], b: list[float]) -> dict[str, float]:
    combined = [(x, 0) for x in a] + [(x, 1) for x in b]
    combined.sort(key=lambda item: item[0])
    ranks = [0.0] * len(combined)
    i = 0
    while i < len(combined):
        j = i + 1
        while j < len(combined) and combined[j][0] == combined[i][0]:
            j += 1
        rank = (i + 1 + j) / 2
        for k in range(i, j):
            ranks[k] = rank
        i = j
    rank_a = sum(rank for rank, (_, group) in zip(ranks, combined) if group == 0)
    n1, n2 = len(a), len(b)
    u1 = rank_a - n1 * (n1 + 1) / 2
    mean = n1 * n2 / 2
    std = math.sqrt(n1 * n2 * (n1 + n2 + 1) / 12)
    z = 0.0 if std == 0 else (u1 - mean) / std
    p_two_sided = math.erfc(abs(z) / math.sqrt(2))
    return {"u": round(u1, 6), "z_aprox": round(z, 6), "p_aprox": round(p_two_sided, 6)}


def vargha_delaney_a12(a: list[float], b: list[float]) -> float:
    wins = ties = 0
    for x in a:
        for y in b:
            if x > y:
                wins += 1
            elif x == y:
                ties += 1
    return round((wins + 0.5 * ties) / (len(a) * len(b)), 6)


def compensation_convergence_ms(db_path: Path) -> float:
    import sqlite3
    from contextlib import closing

    with closing(sqlite3.connect(db_path)) as db:
        rows = db.execute(
            "SELECT elapsed_ms FROM events WHERE participant='coordinator' AND phase='compensate'"
        ).fetchall()
    return percentile([float(row[0]) for row in rows], 0.95) if rows else 0.0


def run_condition(
    output: Path,
    coord: str,
    concurrencia: int,
    fallo: str,
    repeticion: int,
    args: argparse.Namespace,
) -> dict:
    seed = args.seed + repeticion * 100_000 + concurrencia * 100 + len(coord) * 10 + len(fallo)
    db_path = output / "db" / f"{coord}-{fallo}-c{concurrencia}-r{repeticion}.db"
    lab = Lab(db_path, coord, FaultInjector(fallo, 0.0 if fallo == "none" else args.fault_probability, seed, args.delay_seconds))
    lab.reset(initial_stock=concurrencia * 3 + 10)
    cases = [
        Case(i + 1, 1, case.quantity, case.amount, fallo, 0.0 if fallo == "none" else args.fault_probability, seed * 1000 + i)
        for i, case in enumerate(make_cases(concurrencia, seed, args.fault_probability, (fallo,)))
    ]

    if args.warmup_seconds > 0:
        # Calentamiento real y descartado: usa otra base para no contaminar la medición.
        warmup_path = output / "warmup" / f"{coord}-{fallo}-c{concurrencia}-r{repeticion}.db"
        warmup_lab = Lab(warmup_path, coord, FaultInjector("none", 0.0, seed, args.delay_seconds))
        warmup_lab.reset(initial_stock=1_000_000_000)
        deadline = time.monotonic() + args.warmup_seconds
        warmup_round = 0
        warmup_workers = min(concurrencia, args.warmup_workers)
        while time.monotonic() < deadline:
            warmup_round += 1
            warmup_cases = [Case(i + 1, 1, 1, 1000, "none", 0.0,
                                 seed * 1_000_000 + warmup_round * warmup_workers + i)
                            for i in range(warmup_workers)]
            with ThreadPoolExecutor(max_workers=warmup_workers) as pool:
                list(pool.map(warmup_lab.purchase, warmup_cases))

    tracemalloc.start()
    cpu_0 = time.process_time()
    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrencia) as pool:
        rows = list(pool.map(lab.purchase, cases))
    duracion = time.perf_counter() - t0
    cpu = time.process_time() - cpu_0
    _, peak = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    report = oracle(db_path)
    violations = sum(int(check["violations"]) for check in report["checks"])
    confirmed = sum(1 for row in rows if row["success"])
    aborted = len(rows) - confirmed
    latencies = [float(row["elapsed_ms"]) for row in rows]
    return {
        "coord": coord,
        "concurrencia": concurrencia,
        "fallo": fallo,
        "repeticion": repeticion,
        "seed": seed,
        "warmup_seconds": args.warmup_seconds,
        "delay_seconds": args.delay_seconds,
        "fault_probability": 0.0 if fallo == "none" else args.fault_probability,
        "operaciones": len(rows),
        "confirmadas": confirmed,
        "abortadas": aborted,
        "inconsistencias": violations,
        "tasa_inconsistencia": round(violations / len(rows), 6),
        "tasa_abortos": round(aborted / len(rows), 6),
        "latencia_pago_p50_ms": round(percentile(latencies, 0.50), 3),
        "latencia_pago_p95_ms": round(percentile(latencies, 0.95), 3),
        "latencia_pago_p99_ms": round(percentile(latencies, 0.99), 3),
        "ordenes_confirmadas_por_segundo": round(confirmed / duracion if duracion else 0.0, 6),
        "convergencia_compensacion_ms": round(compensation_convergence_ms(db_path), 3),
        "cpu_segundos": round(cpu, 6),
        "memoria_pico_mb": round(peak / 1024 / 1024, 6),
        "duracion_segundos": round(duracion, 6),
        "oracle_pass": report["pass"],
    }


def analyze(raw_rows: list[dict]) -> tuple[list[dict], list[dict]]:
    groups: dict[tuple[str, int, str], list[dict]] = {}
    for row in raw_rows:
        groups.setdefault((row["coord"], int(row["concurrencia"]), row["fallo"]), []).append(row)

    summary = []
    for key, rows in sorted(groups.items()):
        coord, concurrencia, fallo = key
        lat95 = [float(row["latencia_pago_p95_ms"]) for row in rows]
        throughput = [float(row["ordenes_confirmadas_por_segundo"]) for row in rows]
        aborts = sum(int(row["abortadas"]) for row in rows)
        inconsistencies = sum(int(row["inconsistencias"]) for row in rows)
        ops = sum(int(row["operaciones"]) for row in rows)
        lat_ci = bootstrap_median_ci(lat95)
        thr_ci = bootstrap_median_ci(throughput)
        inc_ci = wilson_interval(inconsistencies, ops)
        abort_ci = wilson_interval(aborts, ops)
        summary.append({
            "coord": coord,
            "concurrencia": concurrencia,
            "fallo": fallo,
            "n": len(rows),
            "latencia_p95_mediana_ms": round(statistics.median(lat95), 3),
            "latencia_p95_ic95_inf_ms": round(lat_ci[0], 3),
            "latencia_p95_ic95_sup_ms": round(lat_ci[1], 3),
            "throughput_mediana_ops_s": round(statistics.median(throughput), 6),
            "throughput_ic95_inf_ops_s": round(thr_ci[0], 6),
            "throughput_ic95_sup_ops_s": round(thr_ci[1], 6),
            "tasa_inconsistencia": round(inconsistencies / ops, 6),
            "tasa_inconsistencia_ic95_inf": round(inc_ci[0], 6),
            "tasa_inconsistencia_ic95_sup": round(inc_ci[1], 6),
            "tasa_abortos": round(aborts / ops, 6),
            "tasa_abortos_ic95_inf": round(abort_ci[0], 6),
            "tasa_abortos_ic95_sup": round(abort_ci[1], 6),
            "cpu_mediana_segundos": round(statistics.median(float(row["cpu_segundos"]) for row in rows), 6),
            "memoria_pico_mediana_mb": round(statistics.median(float(row["memoria_pico_mb"]) for row in rows), 6),
            "oracle_pass_all": all(str(row["oracle_pass"]).lower() == "true" or row["oracle_pass"] is True for row in rows),
        })

    comparisons = []
    for concurrencia in sorted({int(row["concurrencia"]) for row in raw_rows}):
        for fallo in FALLOS:
            a = [float(row["latencia_pago_p95_ms"]) for row in raw_rows if row["coord"] == "2pc" and int(row["concurrencia"]) == concurrencia and row["fallo"] == fallo]
            b = [float(row["latencia_pago_p95_ms"]) for row in raw_rows if row["coord"] == "saga" and int(row["concurrencia"]) == concurrencia and row["fallo"] == fallo]
            if a and b:
                mw = mann_whitney_u(a, b)
                comparisons.append({
                    "metrica": "latencia_pago_p95_ms",
                    "grupo_a": f"2pc-c{concurrencia}-{fallo}",
                    "grupo_b": f"saga-c{concurrencia}-{fallo}",
                    **mw,
                    "a12_a_mayor_b": vargha_delaney_a12(a, b),
                })
    return summary, comparisons


def evaluate_compatibility(case_file: Path, output: Path) -> list[dict]:
    cases = json.loads(case_file.read_text(encoding="utf-8"))
    rows = []
    for case in cases:
        reasons = []
        if case["cpu_socket"] != case["motherboard_socket"]:
            reasons.append("socket_incompatible")
        if int(case["ram_gb"]) < 16:
            reasons.append("ram_inferior_16gb")
        if int(case["psu_watts"]) < (int(case["cpu_tdp"]) + int(case["gpu_tdp"])) * 1.7:
            reasons.append("fuente_sin_margen_1_7x")
        predicted = not reasons
        expected = bool(case["expected_compatible"])
        rows.append({
            "case_id": case["case_id"],
            "expected_compatible": expected,
            "predicted_compatible": predicted,
            "true_positive": expected and predicted,
            "true_negative": (not expected) and (not predicted),
            "false_positive": (not expected) and predicted,
            "false_negative": expected and (not predicted),
            "predicted_reasons": "|".join(reasons),
        })
    with (output / "compatibilidad_resultados.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    return rows


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def write_boxplot_svg(path: Path, rows: list[dict], metric: str) -> None:
    labels = []
    boxes = []
    for coord in COORDS:
        for fallo in FALLOS:
            vals = [float(r[metric]) for r in rows if r["coord"] == coord and r["fallo"] == fallo]
            if vals:
                labels.append(f"{coord}\n{fallo}")
                boxes.append((percentile(vals, 0.25), percentile(vals, 0.5), percentile(vals, 0.75), min(vals), max(vals)))
    width, height = 1100, 520
    margin = 70
    all_vals = [item for box in boxes for item in box]
    ymin, ymax = min(all_vals), max(all_vals)
    span = ymax - ymin or 1

    def y(v: float) -> float:
        return height - margin - (v - ymin) / span * (height - 2 * margin)

    step = (width - 2 * margin) / max(1, len(boxes))
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">']
    parts.append('<rect width="100%" height="100%" fill="white"/>')
    parts.append(f'<text x="{width/2}" y="32" text-anchor="middle" font-family="Arial" font-size="22">Boxplot {metric}</text>')
    parts.append(f'<line x1="{margin}" y1="{height-margin}" x2="{width-margin}" y2="{height-margin}" stroke="#222"/>')
    parts.append(f'<line x1="{margin}" y1="{margin}" x2="{margin}" y2="{height-margin}" stroke="#222"/>')
    for i, (q1, med, q3, low, high) in enumerate(boxes):
        x = margin + step * (i + 0.5)
        boxw = min(60, step * 0.55)
        parts.append(f'<line x1="{x}" y1="{y(low)}" x2="{x}" y2="{y(high)}" stroke="#555"/>')
        parts.append(f'<rect x="{x-boxw/2}" y="{y(q3)}" width="{boxw}" height="{max(1, y(q1)-y(q3))}" fill="#8fb6d9" stroke="#1f4e79"/>')
        parts.append(f'<line x1="{x-boxw/2}" y1="{y(med)}" x2="{x+boxw/2}" y2="{y(med)}" stroke="#c43" stroke-width="3"/>')
        safe = labels[i].replace("\n", " ")
        parts.append(f'<text x="{x}" y="{height-28}" text-anchor="middle" font-family="Arial" font-size="12">{safe}</text>')
    parts.append(f'<text x="18" y="{height/2}" transform="rotate(-90 18 {height/2})" text-anchor="middle" font-family="Arial" font-size="14">{metric}</text>')
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def write_threats(path: Path, args: argparse.Namespace) -> None:
    text = f"""# Amenazas a la validez - Paso 8

## Interna

- El banco se ejecuta en una sola maquina y comparte CPU con el sistema operativo.
- La ejecucion usa `warmup_seconds={args.warmup_seconds}` de carga real descartada y `delay_seconds={args.delay_seconds}` para el fallo de temporizacion.
- SQLite modela los datos aislados de Inventario, Pagos y Ordenes; no sustituye una medicion de red real entre microservicios.
- Se eliminó el candado global de Python. SQLite conserva únicamente sus bloqueos transaccionales propios; el oráculo comprueba además que el stock global cuadre con todos los movimientos para detectar actualizaciones perdidas.

## Externa

- Los casos son sinteticos y concentran compradores sobre un producto para forzar contencion; no representan toda la variedad de una tienda real.
- Los niveles 50, 100, 200 y 400 usuarios simulan simultaneidad del checkout, no trafico mixto de navegacion, catalogo y administracion.

## De Constructo

- La inconsistencia observable se operacionaliza como violaciones del oraculo: pago exacto, descuento unico, stock nunca negativo y compensacion completa.
- La convergencia de compensacion usa eventos internos del banco; no incluye latencia de una pasarela de pagos real.

## De Conclusion

- Se usan {args.repeticiones} repeticiones por condicion. Con 12 repeticiones, la prueba U exacta puede superar el umbral Bonferroni de 0.05/12 bajo separación extrema.
- Mann-Whitney U y A12 comparan tendencia de latencias entre 2PC y Saga; no prueban causalidad fuera del banco definido.
"""
    path.write_text(text, encoding="utf-8")


def parser() -> argparse.ArgumentParser:
    cli = argparse.ArgumentParser()
    cli.add_argument("--output", type=Path, default=ROOT / "experiments" / "paso8" / "resultados")
    cli.add_argument("--repeticiones", type=int, default=12)
    cli.add_argument("--concurrencias", type=int, nargs="+", default=CONCURRENCIAS)
    cli.add_argument("--fault-probability", type=float, default=0.10)
    cli.add_argument("--delay-seconds", type=float, default=5.0)
    cli.add_argument("--warmup-seconds", type=float, default=60.0)
    cli.add_argument("--warmup-workers", type=int, default=8,
                     help="máximo de compradores del calentamiento descartado")
    cli.add_argument("--seed", type=int, default=2026)
    cli.add_argument("--compatibility-cases", type=Path, default=ROOT / "experiments" / "paso7" / "evidence" / "compatibility-case-bank.json")
    return cli


def main() -> int:
    args = parser().parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "db").mkdir(parents=True, exist_ok=True)

    raw_rows = []
    total = len(COORDS) * len(args.concurrencias) * len(FALLOS) * args.repeticiones
    done = 0
    for coord in COORDS:
        for concurrencia in args.concurrencias:
            for fallo in FALLOS:
                for repeticion in range(1, args.repeticiones + 1):
                    row = run_condition(args.output, coord, concurrencia, fallo, repeticion, args)
                    raw_rows.append(row)
                    done += 1
                    print(f"[{done}/{total}] {coord} c={concurrencia} fallo={fallo} r={repeticion} p95={row['latencia_pago_p95_ms']}ms oracle={row['oracle_pass']}")

    write_csv(args.output / "experimento_crudo.csv", raw_rows)
    summary, comparisons = analyze(raw_rows)
    write_csv(args.output / "experimento_resumen.csv", summary)
    write_csv(args.output / "comparaciones_mann_whitney.csv", comparisons)
    compat_rows = evaluate_compatibility(args.compatibility_cases, args.output)
    compat_total = len(compat_rows)
    compat_summary = {
        "casos": compat_total,
        "aciertos": sum(1 for r in compat_rows if r["true_positive"] or r["true_negative"]),
        "falsos_positivos": sum(1 for r in compat_rows if r["false_positive"]),
        "falsos_negativos": sum(1 for r in compat_rows if r["false_negative"]),
    }
    low, high = wilson_interval(compat_summary["aciertos"], compat_total)
    compat_summary["accuracy_ic95"] = [round(low, 6), round(high, 6)]
    (args.output / "compatibilidad_resumen.json").write_text(json.dumps(compat_summary, indent=2) + "\n", encoding="utf-8")
    write_boxplot_svg(args.output / "boxplot_latencia_p95.svg", raw_rows, "latencia_pago_p95_ms")
    write_boxplot_svg(args.output / "boxplot_throughput.svg", raw_rows, "ordenes_confirmadas_por_segundo")
    write_threats(args.output / "amenazas_validez.md", args)
    metadata = {
        "matriz": f"2 estrategias x {len(args.concurrencias)} concurrencias x 3 modos de pasarela x {args.repeticiones} repeticiones",
        "coords": COORDS,
        "concurrencias": args.concurrencias,
        "fallos": FALLOS,
        "repeticiones": args.repeticiones,
        "fault_probability": args.fault_probability,
        "delay_seconds": args.delay_seconds,
        "warmup_seconds": args.warmup_seconds,
        "seed": args.seed,
    }
    (args.output / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
